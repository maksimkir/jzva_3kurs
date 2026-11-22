package com.example.library.scheduling;

import com.example.library.digest.NewBooksDigestService;
import com.example.library.entity.Book;
import com.example.library.entity.CatalogReport;
import com.example.library.report.CatalogReportService;
import com.example.library.report.ReportPeriod;
import com.example.library.repository.BookRepository;
import com.example.library.repository.CatalogReportRepository;
import com.example.library.scheduling.QuartzItSupport.RecordingMailGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-тест (IT): справжні Liquibase + Quartz JDBC job store + JPA на БД (H2).
 * Планувальник НЕ стартує (auto-startup=false) — завдання не спрацьовують самі під час тесту,
 * але реєструються в QRTZ_* (як при старті застосунку).
 */
@SpringBootTest(classes = QuartzItSupport.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.quartz.auto-startup=false")
class QuartzSchedulingIntegrationTest {

    @DynamicPropertySource
    static void h2(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:lab6it;DB_CLOSE_DELAY=-1");
        for (String p : QuartzItSupport.H2_PROPERTIES) {
            String[] kv = p.split("=", 2);
            registry.add(kv[0], () -> kv[1]);
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment env;
    @Autowired private Scheduler scheduler;
    @Autowired private NewBooksDigestService digestService;
    @Autowired private CatalogReportService reportService;
    @Autowired private BookRepository books;
    @Autowired private CatalogReportRepository reports;
    @Autowired private RecordingMailGateway mail;

    @BeforeEach
    void cleanDomainData() {
        mail.subjects.clear();
        mail.fail = false;
        reports.deleteAll();
        jdbc.update("update books set announced_at = CURRENT_TIMESTAMP"); // лише нові книги вважаються «новинками»
    }

    private Book newBook(String title, String author, String isbn) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn(isbn);
        return books.save(book);
    }

    // ---------- Сценарій: розклад зберігається в БД за допомогою Liquibase ----------
    @Test
    void liquibase_createsAllQuartzTables_andSpringBootDoesNotInitializeSchema() {
        List<String> tables = jdbc.queryForList(
                "select upper(table_name) from information_schema.tables where upper(table_name) like 'QRTZ\\_%' escape '\\' order by 1",
                String.class);

        assertThat(tables).containsExactly("QRTZ_BLOB_TRIGGERS", "QRTZ_CALENDARS", "QRTZ_CRON_TRIGGERS",
                "QRTZ_FIRED_TRIGGERS", "QRTZ_JOB_DETAILS", "QRTZ_LOCKS", "QRTZ_PAUSED_TRIGGER_GRPS",
                "QRTZ_SCHEDULER_STATE", "QRTZ_SIMPLE_TRIGGERS", "QRTZ_SIMPROP_TRIGGERS", "QRTZ_TRIGGERS");
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog where id = '003-quartz-tables'", Integer.class))
                .isEqualTo(1);
        assertThat(env.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(env.getProperty("spring.quartz.job-store-type")).isEqualTo("jdbc");
    }

    @Test
    void scheduler_usesJdbcJobStore_andPersistsJobsAndTriggersFromConfig() throws Exception {
        assertThat(scheduler.getMetaData().getJobStoreClass()).isEqualTo(LocalDataSourceJobStore.class);
        assertThat(scheduler.getMetaData().isJobStoreSupportsPersistence()).isTrue();

        assertThat(jdbc.queryForList("select job_group || '.' || job_name from qrtz_job_details order by 1", String.class))
                .containsExactly("notifications.newBooksDigest", "reports.catalogReport");
        assertThat(jdbc.queryForList("select trigger_group || '.' || trigger_name || '->' || job_name from qrtz_triggers order by 1",
                String.class))
                .containsExactly("notifications.newBooksDigestHourly->newBooksDigest",
                        "reports.catalogReportDaily->catalogReport",
                        "reports.catalogReportWeekly->catalogReport");
        assertThat(jdbc.queryForList("select distinct time_zone_id from qrtz_cron_triggers", String.class))
                .containsExactly("Europe/Kyiv");
        assertThat(jdbc.queryForObject("select is_nonconcurrent from qrtz_job_details where job_name = 'newBooksDigest'",
                Boolean.class)).isTrue();

        // те, що прочитане НАЗАД з БД, зберігає перевизначення тригера і misfire-інструкцію
        CronTrigger weekly = (CronTrigger) scheduler.getTrigger(QuartzJobsConfig.WEEKLY_REPORT_TRIGGER);
        assertThat(weekly.getJobDataMap().getString("period")).isEqualTo("WEEKLY");
        assertThat(weekly.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    // ---------- Сценарій: повторний запуск завдання безпечний ----------
    @Test
    void digest_runTwiceForSameData_secondRunChangesNothing() {
        // Arrange
        newBook("Domain-Driven Design", "Eric Evans", null);
        newBook("Release It!", "Michael Nygard", null);

        // Act
        int first = digestService.sendDigest(50, "readers@library.local");
        int second = digestService.sendDigest(50, "readers@library.local");

        // Assert
        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(mail.subjects).containsExactly("Новинки бібліотеки (2)");
        assertThat(jdbc.queryForObject("select count(*) from books where announced_at is null", Integer.class)).isZero();
    }

    @Test
    void digest_mailFails_nothingIsMarked_soNextAttemptSendsTheSameBooks() {
        // Arrange
        newBook("Accelerate", "Nicole Forsgren", null);
        mail.fail = true;

        // Act 1: збій SMTP -> виняток, транзакція відкочується
        assertThatThrownBy(() -> digestService.sendDigest(50, "readers@library.local"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from books where announced_at is null", Integer.class)).isEqualTo(1);

        // Act 2: SMTP «піднявся»
        mail.fail = false;
        assertThat(digestService.sendDigest(50, "readers@library.local")).isEqualTo(1);
        assertThat(mail.subjects).containsExactly("Новинки бібліотеки (1)");
    }

    @Test
    void report_runTwiceForSamePeriod_secondRunChangesNothing() {
        // Act
        CatalogReport first = reportService.generate(ReportPeriod.DAILY, 5);
        CatalogReport second = reportService.generate(ReportPeriod.DAILY, 5);

        // Assert: один рядок, ті самі значення, той самий generatedAt
        List<CatalogReport> all = reports.findAll();
        assertThat(all).hasSize(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(all.get(0).getGeneratedAt()).isEqualTo(first.getGeneratedAt());
        assertThat(all.get(0).getTotalBooks()).isEqualTo(books.count());
    }

    @Test
    void report_catalogChangedBetweenRuns_samePeriodRowIsUpdatedNotDuplicated() {
        // Arrange
        CatalogReport first = reportService.generate(ReportPeriod.WEEKLY, 10);
        newBook("Working Effectively with Legacy Code", "Michael Feathers", "9780131177055");

        // Act
        CatalogReport second = reportService.generate(ReportPeriod.WEEKLY, 10);

        // Assert
        assertThat(reports.findAll()).hasSize(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getTotalBooks()).isEqualTo(first.getTotalBooks() + 1);
        assertThat(second.getBooksWithIsbn()).isEqualTo(first.getBooksWithIsbn() + 1);
        assertThat(second.getPeriodStart().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    }
}

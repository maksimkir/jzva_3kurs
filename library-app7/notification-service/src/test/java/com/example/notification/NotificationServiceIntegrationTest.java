package com.example.notification;

import com.example.library.common.notification.BookCreatedNotification;
import com.example.notification.config.InternalApiKeyFilter;
import com.example.notification.digest.NewBooksDigestService;
import com.example.notification.mail.MailGateway;
import com.example.notification.scheduling.QuartzJobsConfig;
import com.example.notification.store.PendingAnnouncementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT: увесь notification-service як окремий Spring Boot застосунок (випадковий порт) на H2:
 * справжні Liquibase, JPA, Quartz JDBC store і HTTP-ендпоінт з ключем.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notification;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate",
        "spring.quartz.properties.org.quartz.jobStore.isClustered=false",
        "spring.quartz.auto-startup=false",
        "notification.internal-api-key=test-key"
})
class NotificationServiceIntegrationTest {

    @TestConfiguration
    static class Mail {
        @Bean
        @Primary
        RecordingMail recordingMail() {
            return new RecordingMail();
        }
    }

    static class RecordingMail implements MailGateway {
        final List<String> subjects = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @Override
        public void send(String to, String subject, String body) {
            if (fail) {
                throw new IllegalStateException("SMTP down (test)");
            }
            subjects.add(subject);
        }
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Scheduler scheduler;
    @Autowired private NewBooksDigestService digest;
    @Autowired private PendingAnnouncementRepository repository;
    @Autowired private RecordingMail mail;

    @BeforeEach
    void clean() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory()); // HttpURLConnection не віддає тіло 401 для POST
        repository.deleteAll();
        mail.subjects.clear();
        mail.fail = false;
    }

    private ResponseEntity<Map> post(Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null) {
            headers.set(InternalApiKeyFilter.HEADER, key);
        }
        return rest.postForEntity("/internal/notifications/book-created", new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void liquibase_createsOwnTablesAndQuartzTables_andJobIsPersisted() throws Exception {
        List<String> tables = jdbc.queryForList("select upper(table_name) from information_schema.tables "
                + "where upper(table_name) like 'QRTZ\\_%' escape '\\' or upper(table_name) = 'PENDING_ANNOUNCEMENTS'", String.class);
        assertThat(tables).hasSize(12).contains("PENDING_ANNOUNCEMENTS", "QRTZ_JOB_DETAILS", "QRTZ_TRIGGERS");

        assertThat(scheduler.getMetaData().getJobStoreClass()).isEqualTo(LocalDataSourceJobStore.class);
        assertThat(scheduler.checkExists(QuartzJobsConfig.DIGEST_JOB)).isTrue();
        assertThat(jdbc.queryForList("select trigger_name from qrtz_triggers", String.class)).containsExactly("newBooksDigestHourly");
    }

    @Test
    void internalApi_withoutOrWithWrongKey_returns401() {
        BookCreatedNotification body = new BookCreatedNotification(1L, "A", "B");

        assertThat(post(body, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(body, "wrong").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(repository.count()).isZero();
    }

    @Test
    void internalApi_incompleteBody_returns400() {
        assertThat(post(new BookCreatedNotification(null, "A", "B"), "test-key").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookCreated_sameBookTwice_storedOnceAndEmailedOnce() {
        BookCreatedNotification body = new BookCreatedNotification(42L, "Domain-Driven Design", "Eric Evans");

        ResponseEntity<Map> first = post(body, "test-key");
        ResponseEntity<Map> second = post(body, "test-key");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(first.getBody()).containsEntry("result", "ACCEPTED");
        assertThat(second.getBody()).containsEntry("result", "DUPLICATE");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(mail.subjects).containsExactly("Нова книга в бібліотеці: Domain-Driven Design");
    }

    @Test
    void digest_runTwice_secondRunChangesNothing() {
        post(new BookCreatedNotification(1L, "Release It!", "Michael Nygard"), "test-key");
        post(new BookCreatedNotification(2L, "Accelerate", "Nicole Forsgren"), "test-key");
        mail.subjects.clear();

        assertThat(digest.sendDigest(50, "readers@library.local")).isEqualTo(2);
        assertThat(digest.sendDigest(50, "readers@library.local")).isZero();
        assertThat(mail.subjects).containsExactly("Новинки бібліотеки (2)");
    }

    @Test
    void digest_mailFails_nothingMarked_nextAttemptSendsSameBooks() {
        post(new BookCreatedNotification(3L, "Refactoring", "Martin Fowler"), "test-key");
        mail.subjects.clear();
        mail.fail = true;

        assertThatThrownBy(() -> digest.sendDigest(50, "readers@library.local")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from pending_announcements where announced_at is null", Integer.class)).isEqualTo(1);

        mail.fail = false;
        assertThat(digest.sendDigest(50, "readers@library.local")).isEqualTo(1);
    }
}

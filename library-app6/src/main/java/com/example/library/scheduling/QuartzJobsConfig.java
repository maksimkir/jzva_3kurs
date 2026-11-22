package com.example.library.scheduling;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ЛР6 — ЄДИНЕ місце, де оголошено всі завдання і тригери.
 * <ul>
 *   <li>кожне завдання/тригер має явні name + group;</li>
 *   <li>тригер прив'язаний до завдання через JobKey ({@code forJob(...)});</li>
 *   <li>значення параметрів за замовчуванням — у JobDataMap завдання; тригер перевизначає лише потрібні ключі;</li>
 *   <li>у кожного тригера явна misfire-інструкція; cron-тригери мають явний часовий пояс.</li>
 * </ul>
 * Spring Boot (QuartzAutoConfiguration) реєструє ці біни в планувальнику з JDBC job store.
 * Значення в JobDataMap — рядки ({@code org.quartz.jobStore.useProperties=true}), щоб у БД не серіалізувались Java-об'єкти.
 */
@Configuration
public class QuartzJobsConfig {

    public static final String NOTIFICATIONS_GROUP = "notifications";
    public static final String REPORTS_GROUP = "reports";

    public static final JobKey DIGEST_JOB = JobKey.jobKey("newBooksDigest", NOTIFICATIONS_GROUP);
    public static final TriggerKey DIGEST_TRIGGER = TriggerKey.triggerKey("newBooksDigestHourly", NOTIFICATIONS_GROUP);

    public static final JobKey REPORT_JOB = JobKey.jobKey("catalogReport", REPORTS_GROUP);
    public static final TriggerKey DAILY_REPORT_TRIGGER = TriggerKey.triggerKey("catalogReportDaily", REPORTS_GROUP);
    public static final TriggerKey WEEKLY_REPORT_TRIGGER = TriggerKey.triggerKey("catalogReportWeekly", REPORTS_GROUP);

    private final SchedulingProperties properties;

    public QuartzJobsConfig(SchedulingProperties properties) {
        this.properties = properties;
    }

    // ---------------- Завдання 1: дайджест новинок (1 тригер) ----------------

    @Bean
    public JobDetail newBooksDigestJobDetail() {
        return JobBuilder.newJob(NewBooksDigestJob.class)
                .withIdentity(DIGEST_JOB)
                .withDescription("Лист-дайджест про книги, що ще не анонсувались")
                .usingJobData(NewBooksDigestJob.BATCH_SIZE, "50")
                .usingJobData(NewBooksDigestJob.RECIPIENTS, "readers@library.local")
                .usingJobData(NewBooksDigestJob.MAX_RETRIES, "2")
                .storeDurably()
                .requestRecovery() // якщо інстанс «впав» посеред виконання — після рестарту завдання виконається знову
                .build();
    }

    @Bean
    public Trigger newBooksDigestTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity(DIGEST_TRIGGER)
                .forJob(DIGEST_JOB)
                .withDescription("Кожні N хвилин")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(properties.digestIntervalMinutes())
                        .repeatForever()
                        // пропущені запуски (застосунок був вимкнений) не «доганяємо» пачкою — просто наступний за розкладом
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }

    // ---------------- Завдання 2: звіт по каталогу (2 тригери) ----------------

    @Bean
    public JobDetail catalogReportJobDetail() {
        return JobBuilder.newJob(CatalogReportJob.class)
                .withIdentity(REPORT_JOB)
                .withDescription("Знімок статистики каталогу")
                .usingJobData(CatalogReportJob.PERIOD, "DAILY")
                .usingJobData(CatalogReportJob.TOP_AUTHORS, "5")
                .storeDurably()
                .build();
    }

    /** Щодня о 06:00 за Києвом; параметри — значення завдання за замовчуванням. */
    @Bean
    public Trigger catalogReportDailyTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity(DAILY_REPORT_TRIGGER)
                .forJob(REPORT_JOB)
                .withDescription("Щоденний звіт")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.dailyReportCron())
                        .inTimeZone(properties.resolvedTimeZone())
                        // пропустили 06:00 (деплой/рестарт) — сформувати звіт один раз одразу після старту
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    /** Щопонеділка о 07:00 за Києвом; перевизначає period і topAuthors, інші ключі — за замовчуванням. */
    @Bean
    public Trigger catalogReportWeeklyTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity(WEEKLY_REPORT_TRIGGER)
                .forJob(REPORT_JOB)
                .withDescription("Щотижневий звіт")
                .usingJobData(CatalogReportJob.PERIOD, "WEEKLY")
                .usingJobData(CatalogReportJob.TOP_AUTHORS, "10")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.weeklyReportCron())
                        .inTimeZone(properties.resolvedTimeZone())
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}

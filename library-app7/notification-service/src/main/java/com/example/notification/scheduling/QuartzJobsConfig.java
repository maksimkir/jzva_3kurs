package com.example.notification.scheduling;

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
 * ЛР6/ЛР7 — завдання і тригер notification-service (дайджест новинок). Звіт по каталогу живе в catalog-service.
 * Значення параметрів за замовчуванням — у JobDataMap завдання (рядки: useProperties=true); явна misfire-інструкція.
 */
@Configuration
public class QuartzJobsConfig {

    public static final String NOTIFICATIONS_GROUP = "notifications";
    public static final JobKey DIGEST_JOB = JobKey.jobKey("newBooksDigest", NOTIFICATIONS_GROUP);
    public static final TriggerKey DIGEST_TRIGGER = TriggerKey.triggerKey("newBooksDigestHourly", NOTIFICATIONS_GROUP);

    private final SchedulingProperties properties;

    public QuartzJobsConfig(SchedulingProperties properties) {
        this.properties = properties;
    }

    @Bean
    public JobDetail newBooksDigestJobDetail() {
        return JobBuilder.newJob(NewBooksDigestJob.class)
                .withIdentity(DIGEST_JOB)
                .withDescription("Лист-дайджест про книги, що ще не анонсувались")
                .usingJobData(NewBooksDigestJob.BATCH_SIZE, "50")
                .usingJobData(NewBooksDigestJob.RECIPIENTS, "readers@library.local")
                .usingJobData(NewBooksDigestJob.MAX_RETRIES, "2")
                .storeDurably()
                .requestRecovery() // інстанс «впав» посеред виконання — після рестарту завдання виконається знову
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
                        // пропущені запуски не «доганяємо» пачкою — просто наступний за розкладом
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }
}

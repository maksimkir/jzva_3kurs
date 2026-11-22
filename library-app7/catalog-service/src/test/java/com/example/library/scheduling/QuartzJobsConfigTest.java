package com.example.library.scheduling;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit-тести конфігурації завдань/тригерів (без Spring): імена, групи, JobKey, JobDataMap, misfire, часовий пояс.
 */
class QuartzJobsConfigTest {

    private final QuartzJobsConfig config = new QuartzJobsConfig(
            new SchedulingProperties("Europe/Kyiv", "0 0 6 * * ?", "0 0 7 ? * MON"));

    private final JobDetail reportJob = config.catalogReportJobDetail();
    private final Trigger dailyTrigger = config.catalogReportDailyTrigger();
    private final Trigger weeklyTrigger = config.catalogReportWeeklyTrigger();

    // ---------- Сценарій: завдання і тригери оголошено в одному місці, з name/group і JobKey ----------
    @Test
    void jobs_haveExplicitNameAndGroup() {
        assertThat(reportJob.getKey().getName()).isEqualTo("catalogReport");
        assertThat(reportJob.getKey().getGroup()).isEqualTo("reports");
        assertThat(reportJob.getJobClass()).isEqualTo(CatalogReportJob.class);
        assertThat(reportJob.isDurable()).isTrue();
    }

    @Test
    void triggers_haveExplicitNameAndGroup_andPointToTheirJobByJobKey() {
        assertThat(dailyTrigger.getKey().getName()).isEqualTo("catalogReportDaily");
        assertThat(weeklyTrigger.getKey().getName()).isEqualTo("catalogReportWeekly");
        for (Trigger t : List.of(dailyTrigger, weeklyTrigger)) {
            assertThat(t.getKey().getGroup()).isEqualTo("reports");
            assertThat(t.getJobKey()).as("both report triggers -> one job").isEqualTo(reportJob.getKey());
        }
    }

    // ---------- Сценарій: параметри тригера мають пріоритет над значеннями завдання ----------
    @Test
    void jobDetails_containDefaultParameters() {
        assertThat(reportJob.getJobDataMap().getWrappedMap())
                .containsEntry("period", "DAILY")
                .containsEntry("topAuthors", "5");
    }

    @Test
    void mergedJobDataMap_weeklyTrigger_overridesSomeKeys() {
        // Act: об'єднання — РЕАЛЬНИМ кодом Quartz (JobExecutionContextImpl.getMergedJobDataMap)
        JobDataMap merged = mergeLikeQuartz(reportJob, weeklyTrigger);

        // Assert: ключі з тригера перемогли, решта — значення завдання
        assertThat(merged.getString("period")).isEqualTo("WEEKLY");
        assertThat(merged.getString("topAuthors")).isEqualTo("10");
        assertThat(merged.keySet()).containsExactlyInAnyOrder("period", "topAuthors");
    }

    @Test
    void mergedJobDataMap_dailyTrigger_keepsJobDefaults() {
        JobDataMap merged = mergeLikeQuartz(reportJob, dailyTrigger);

        assertThat(merged.getString("period")).isEqualTo("DAILY");
        assertThat(merged.getString("topAuthors")).isEqualTo("5");
    }

    @Test
    void mergedJobDataMap_partialOverride_untouchedKeysKeepDefaults() {
        // Arrange: тригер перевизначає лише один ключ
        Trigger onlyTop = dailyTrigger.getTriggerBuilder().usingJobData("topAuthors", "3").build();

        // Act
        JobDataMap merged = mergeLikeQuartz(reportJob, onlyTop);

        // Assert
        assertThat(merged.getString("topAuthors")).isEqualTo("3");
        assertThat(merged.getString("period")).isEqualTo("DAILY");
    }

    @Test
    void jobData_containsOnlyStrings_compatibleWithUseProperties() {
        for (JobDataMap map : List.of(reportJob.getJobDataMap(), weeklyTrigger.getJobDataMap())) {
            assertThat(map.getWrappedMap().values()).allMatch(String.class::isInstance);
        }
    }

    // ---------- Сценарій: у кожного тригера явна misfire-інструкція ----------
    @Test
    void triggers_haveExplicitMisfireInstructions() {
        assertThat(dailyTrigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(weeklyTrigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        for (Trigger t : List.of(dailyTrigger, weeklyTrigger)) {
            assertThat(t.getMisfireInstruction()).as("not the implicit SMART_POLICY").isNotEqualTo(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);
        }
    }

    // ---------- Сценарій: cron-тригери не залежать від пояса сервера ----------
    @Test
    void cronTriggers_haveExplicitTimeZoneAndSchedule() {
        CronTrigger daily = (CronTrigger) dailyTrigger;
        CronTrigger weekly = (CronTrigger) weeklyTrigger;

        assertThat(daily.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(weekly.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(daily.getCronExpression()).isEqualTo("0 0 6 * * ?");
        assertThat(weekly.getCronExpression()).isEqualTo("0 0 7 ? * MON");
    }

    @Test
    void cronTrigger_unknownTimeZoneId_silentlyFallsBackToGmt() {
        // Arrange: одруківка в назві пояса
        QuartzJobsConfig typoConfig = new QuartzJobsConfig(
                new SchedulingProperties("Europe/Kyev", "0 0 6 * * ?", "0 0 7 ? * MON"));

        // Act
        CronTrigger daily = (CronTrigger) typoConfig.catalogReportDailyTrigger();

        // Assert: java.util.TimeZone не кидає виняток, а підставляє GMT (QuartzTimeZones пише про це WARN)
        assertThat(daily.getTimeZone().getID()).isEqualTo("GMT");
    }

    /** Те саме, що робить Quartz перед execute(): JobDetail.jobDataMap + Trigger.jobDataMap (тригер перезаписує). */
    private static JobDataMap mergeLikeQuartz(JobDetail job, Trigger trigger) {
        Date now = new Date();
        TriggerFiredBundle bundle = new TriggerFiredBundle(job, (OperableTrigger) trigger, null, false, now, now, null, null);
        return new JobExecutionContextImpl(mock(Scheduler.class), bundle, null).getMergedJobDataMap();
    }
}

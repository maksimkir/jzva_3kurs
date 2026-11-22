package com.example.notification.scheduling;

import com.example.notification.digest.NewBooksDigestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести (без Spring) конфігурації і «легкого» NewBooksDigestJob, перенесені з ЛР6:
 * name/group/JobKey, JobDataMap за замовчуванням і перевизначення, misfire, явні помилки параметрів,
 * повтор з обмеженням (без нескінченного циклу).
 */
@ExtendWith(MockitoExtension.class)
class DigestSchedulingTest {

    @Mock private NewBooksDigestService digestService;
    @Mock private JobExecutionContext context;

    private final QuartzJobsConfig config = new QuartzJobsConfig(new SchedulingProperties(60));
    private final JobDetail job = config.newBooksDigestJobDetail();
    private final Trigger trigger = config.newBooksDigestTrigger();

    private void givenMerged(Map<String, String> merged, int refireCount) {
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(merged));
        lenient().when(context.getJobDetail()).thenReturn(job);
        lenient().when(context.getRefireCount()).thenReturn(refireCount);
    }

    private static Map<String, String> defaults() {
        return Map.of("batchSize", "50", "recipients", "readers@library.local", "maxRetries", "2");
    }

    // ---------- конфігурація ----------
    @Test
    void jobAndTrigger_haveNameGroupAndJobKey() {
        assertThat(job.getKey().getName()).isEqualTo("newBooksDigest");
        assertThat(job.getKey().getGroup()).isEqualTo("notifications");
        assertThat(job.getJobClass()).isEqualTo(NewBooksDigestJob.class);
        assertThat(job.isDurable()).isTrue();
        assertThat(job.requestsRecovery()).isTrue();
        assertThat(trigger.getKey()).isEqualTo(QuartzJobsConfig.DIGEST_TRIGGER);
        assertThat(trigger.getJobKey()).isEqualTo(job.getKey());
    }

    @Test
    void trigger_hasExplicitMisfireInstructionAndInterval() {
        assertThat(trigger).isInstanceOf(SimpleTrigger.class);
        assertThat(trigger.getMisfireInstruction()).isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        assertThat(((SimpleTrigger) trigger).getRepeatInterval()).isEqualTo(60L * 60 * 1000);
    }

    @Test
    void mergedJobDataMap_triggerOverride_winsAndOtherKeysKeepDefaults() {
        Trigger onlyBatch = trigger.getTriggerBuilder().usingJobData("batchSize", "5").build();
        Date now = new Date();
        JobDataMap merged = new JobExecutionContextImpl(mock(Scheduler.class),
                new TriggerFiredBundle(job, (OperableTrigger) onlyBatch, null, false, now, now, null, null), null)
                .getMergedJobDataMap();

        assertThat(merged.getString("batchSize")).isEqualTo("5");
        assertThat(merged.getString("recipients")).isEqualTo("readers@library.local");
        assertThat(merged.getString("maxRetries")).isEqualTo("2");
        assertThat(job.getJobDataMap().getWrappedMap().values()).allMatch(String.class::isInstance);
    }

    // ---------- «легке» завдання ----------
    @Test
    void digestJob_readsParamsAndCallsServiceOnce() throws Exception {
        givenMerged(defaults(), 0);

        new NewBooksDigestJob(digestService).execute(context);

        verify(digestService).sendDigest(50, "readers@library.local");
        verifyNoMoreInteractions(digestService);
    }

    @Test
    void digestJob_isLightAndServiceOwnsTransaction() throws NoSuchMethodException {
        assertThat(NewBooksDigestJob.class.getDeclaredConstructors()[0].getParameterCount()).isEqualTo(1);
        assertThat(NewBooksDigestJob.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(NewBooksDigestService.class.getMethod("sendDigest", int.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(NewBooksDigestJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }

    @Test
    void digestJob_missingKey_throwsExplicitErrorAndDoesNotCallService() {
        givenMerged(Map.of("batchSize", "50", "maxRetries", "2"), 0);

        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("required JobDataMap key 'recipients' is missing");
        verifyNoInteractions(digestService);
    }

    @Test
    void digestJob_typoAndNotANumber_throwExplicitErrors() {
        givenMerged(Map.of("batchSize", "fifty", "recipients", "a@b.c", "maxRetries", "2"), 0);
        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .hasMessageContaining("'batchSize' must be an integer, but was 'fifty'");

        givenMerged(Map.of("batchSize", "5", "recipients", "a@b.c", "maxRetries", "2", "maxRetry", "9"), 0);
        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .hasMessageContaining("unknown JobDataMap key(s) [maxRetry]");
        verifyNoInteractions(digestService);
    }

    @Test
    void digestJob_failureBeforeLimit_requestsImmediateRefire() {
        givenMerged(defaults(), 0);
        when(digestService.sendDigest(50, "readers@library.local")).thenThrow(new IllegalStateException("SMTP down"));

        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isTrue());
    }

    @Test
    void digestJob_failureAfterLimit_givesUpUntilNextScheduledRun() {
        givenMerged(defaults(), 2);
        when(digestService.sendDigest(50, "readers@library.local")).thenThrow(new IllegalStateException("SMTP down"));

        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(ex -> {
                    assertThat(((JobExecutionException) ex).refireImmediately()).isFalse();
                    assertThat(((JobExecutionException) ex).unscheduleAllTriggers()).isFalse();
                });
    }
}

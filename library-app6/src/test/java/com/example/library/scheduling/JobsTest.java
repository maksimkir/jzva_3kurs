package com.example.library.scheduling;

import com.example.library.digest.NewBooksDigestService;
import com.example.library.report.CatalogReportService;
import com.example.library.report.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести «легких» завдань: читають параметри + ОДИН виклик сервісу; помилки параметрів — явні; стратегії збою.
 */
@ExtendWith(MockitoExtension.class)
class JobsTest {

    @Mock private NewBooksDigestService digestService;
    @Mock private CatalogReportService reportService;
    @Mock private JobExecutionContext context;

    private final QuartzJobsConfig config = new QuartzJobsConfig(
            new SchedulingProperties("Europe/Kyiv", 60, "0 0 6 * * ?", "0 0 7 ? * MON"));

    private void givenContext(JobDetail job, Map<String, String> merged, int refireCount) {
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(merged));
        lenient().when(context.getJobDetail()).thenReturn(job);
        lenient().when(context.getRefireCount()).thenReturn(refireCount);
    }

    private void givenDigestDefaults(int refireCount) {
        givenContext(config.newBooksDigestJobDetail(), config.newBooksDigestJobDetail().getJobDataMap().getWrappedMap()
                .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())),
                refireCount);
    }

    // ---------- Сценарій: завдання «легкі» ----------
    @Test
    void digestJob_readsParamsAndCallsServiceOnce() throws Exception {
        // Arrange
        givenDigestDefaults(0);

        // Act
        new NewBooksDigestJob(digestService).execute(context);

        // Assert
        verify(digestService).sendDigest(50, "readers@library.local");
        verifyNoMoreInteractions(digestService);
    }

    @Test
    void reportJob_weeklyOverrides_passedToServiceOnce() throws Exception {
        // Arrange
        givenContext(config.catalogReportJobDetail(), Map.of("period", "WEEKLY", "topAuthors", "10"), 0);

        // Act
        new CatalogReportJob(reportService).execute(context);

        // Assert
        verify(reportService).generate(ReportPeriod.WEEKLY, 10);
        verifyNoMoreInteractions(reportService);
    }

    @Test
    void jobs_haveSingleDependencyAndNoTransactionalAnnotations() {
        for (Class<?> job : new Class<?>[]{NewBooksDigestJob.class, CatalogReportJob.class}) {
            assertThat(job.getDeclaredConstructors()).hasSize(1);
            assertThat(job.getDeclaredConstructors()[0].getParameterCount()).as("one Spring service").isEqualTo(1);
            assertThat(job.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
            for (Method m : job.getDeclaredMethods()) {
                assertThat(m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
            }
        }
    }

    @Test
    void services_ownTheTransactionBoundary() throws NoSuchMethodException {
        assertThat(NewBooksDigestService.class.getMethod("sendDigest", int.class, String.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
        assertThat(CatalogReportService.class.getMethod("generate", ReportPeriod.class, int.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }

    // ---------- Сценарій: невідповідність параметрів -> явна помилка ----------
    @Test
    void digestJob_missingKey_throwsExplicitErrorAndDoesNotCallService() {
        // Arrange: немає recipients
        givenContext(config.newBooksDigestJobDetail(), Map.of("batchSize", "50", "maxRetries", "2"), 0);

        // Act + Assert
        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("required JobDataMap key 'recipients' is missing")
                .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isFalse());
        verifyNoInteractions(digestService);
    }

    @Test
    void reportJob_typoInTriggerKey_throwsExplicitErrorListingExpectedKeys() {
        // Arrange: тригер помилково містить "topAuthor" замість "topAuthors"
        givenContext(config.catalogReportJobDetail(), Map.of("period", "WEEKLY", "topAuthors", "5", "topAuthor", "10"), 0);

        // Act + Assert
        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("unknown JobDataMap key(s) [topAuthor]")
                .hasMessageContaining("[period, topAuthors]");
        verifyNoInteractions(reportService);
    }

    @Test
    void digestJob_notANumber_throwsExplicitError() {
        givenContext(config.newBooksDigestJobDetail(),
                Map.of("batchSize", "fifty", "recipients", "a@b.c", "maxRetries", "2"), 0);

        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'batchSize' must be an integer, but was 'fifty'");
        verifyNoInteractions(digestService);
    }

    @Test
    void reportJob_unknownPeriod_throwsExplicitError() {
        givenContext(config.catalogReportJobDetail(), Map.of("period", "MONTHLY", "topAuthors", "5"), 0);

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'period' has unsupported value 'MONTHLY'");
        verifyNoInteractions(reportService);
    }

    // ---------- Сценарій: невдале виконання обробляється цілеспрямовано ----------
    @Test
    void digestJob_failureBeforeLimit_requestsImmediateRefire() {
        // Arrange: перша спроба (refireCount=0), maxRetries=2
        givenDigestDefaults(0);
        when(digestService.sendDigest(50, "readers@library.local")).thenThrow(new IllegalStateException("SMTP down"));

        // Act + Assert
        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isTrue());
    }

    @Test
    void digestJob_failureAfterLimit_givesUpUntilNextScheduledRun() {
        // Arrange: вже 2 повтори (refireCount=2 == maxRetries)
        givenDigestDefaults(2);
        when(digestService.sendDigest(50, "readers@library.local")).thenThrow(new IllegalStateException("SMTP down"));

        // Act + Assert: більше НЕ refire -> немає нескінченного циклу
        assertThatThrownBy(() -> new NewBooksDigestJob(digestService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    JobExecutionException jee = (JobExecutionException) ex;
                    assertThat(jee.refireImmediately()).isFalse();
                    assertThat(jee.unscheduleAllTriggers()).as("schedule stays active").isFalse();
                });
    }

    @Test
    void reportJob_failure_isRecordedWithoutRefire() {
        // Arrange
        givenContext(config.catalogReportJobDetail(), Map.of("period", "DAILY", "topAuthors", "5"), 0);
        when(reportService.generate(ReportPeriod.DAILY, 5)).thenThrow(new DataAccessResourceFailureException("db down"));

        // Act + Assert: стратегія «зафіксувати й чекати наступного запуску»
        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(ex -> {
                    JobExecutionException jee = (JobExecutionException) ex;
                    assertThat(jee.refireImmediately()).isFalse();
                    assertThat(jee.unscheduleAllTriggers()).isFalse();
                });
    }

    // ---------- Сценарій: паралельне виконання заборонено ----------
    @Test
    void jobs_areAnnotatedWithDisallowConcurrentExecution() {
        assertThat(Arrays.asList(NewBooksDigestJob.class, CatalogReportJob.class))
                .allMatch(c -> c.isAnnotationPresent(DisallowConcurrentExecution.class));
        assertThat(config.newBooksDigestJobDetail().isConcurrentExectionDisallowed()).isTrue();
        assertThat(config.catalogReportJobDetail().isConcurrentExectionDisallowed()).isTrue();
    }
}

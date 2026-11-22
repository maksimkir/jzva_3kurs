package com.example.library.scheduling;

import com.example.library.report.CatalogReportService;
import com.example.library.report.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести «легкого» CatalogReportJob: читає параметри + ОДИН виклик сервісу; помилки параметрів — явні;
 * стратегія збою — зафіксувати й чекати наступного запуску. (Тести дайджесту — у notification-service.)
 */
@ExtendWith(MockitoExtension.class)
class JobsTest {

    @Mock private CatalogReportService reportService;
    @Mock private JobExecutionContext context;

    private final QuartzJobsConfig config = new QuartzJobsConfig(
            new SchedulingProperties("Europe/Kyiv", "0 0 6 * * ?", "0 0 7 ? * MON"));

    private void givenMerged(Map<String, String> merged) {
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(merged));
        lenient().when(context.getJobDetail()).thenReturn(config.catalogReportJobDetail());
    }

    @Test
    void reportJob_jobDefaults_passedToServiceOnce() throws Exception {
        givenMerged(Map.of("period", "DAILY", "topAuthors", "5"));

        new CatalogReportJob(reportService).execute(context);

        verify(reportService).generate(ReportPeriod.DAILY, 5);
        verifyNoMoreInteractions(reportService);
    }

    @Test
    void reportJob_weeklyOverrides_passedToServiceOnce() throws Exception {
        givenMerged(Map.of("period", "WEEKLY", "topAuthors", "10"));

        new CatalogReportJob(reportService).execute(context);

        verify(reportService).generate(ReportPeriod.WEEKLY, 10);
        verifyNoMoreInteractions(reportService);
    }

    @Test
    void reportJob_hasSingleDependencyAndNoTransactionalAnnotations() {
        assertThat(CatalogReportJob.class.getDeclaredConstructors()).hasSize(1);
        assertThat(CatalogReportJob.class.getDeclaredConstructors()[0].getParameterCount()).isEqualTo(1);
        assertThat(CatalogReportJob.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method m : CatalogReportJob.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(Transactional.class)).isFalse();
        }
    }

    @Test
    void service_ownsTheTransactionBoundary() throws NoSuchMethodException {
        assertThat(CatalogReportService.class.getMethod("generate", ReportPeriod.class, int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void reportJob_missingKey_throwsExplicitErrorAndDoesNotCallService() {
        givenMerged(Map.of("period", "DAILY"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("required JobDataMap key 'topAuthors' is missing")
                .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isFalse());
        verifyNoInteractions(reportService);
    }

    @Test
    void reportJob_typoInTriggerKey_throwsExplicitErrorListingExpectedKeys() {
        givenMerged(Map.of("period", "WEEKLY", "topAuthors", "5", "topAuthor", "10"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("unknown JobDataMap key(s) [topAuthor]")
                .hasMessageContaining("[period, topAuthors]");
        verifyNoInteractions(reportService);
    }

    @Test
    void reportJob_notANumber_throwsExplicitError() {
        givenMerged(Map.of("period", "DAILY", "topAuthors", "ten"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'topAuthors' must be an integer, but was 'ten'");
        verifyNoInteractions(reportService);
    }

    @Test
    void reportJob_belowMinimum_throwsExplicitError() {
        givenMerged(Map.of("period", "DAILY", "topAuthors", "0"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'topAuthors' must be >= 1, but was 0");
        verifyNoInteractions(reportService);
    }

    @Test
    void reportJob_unknownPeriod_throwsExplicitError() {
        givenMerged(Map.of("period", "MONTHLY", "topAuthors", "5"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'period' has unsupported value 'MONTHLY'");
        verifyNoInteractions(reportService);
    }

    @Test
    void reportJob_failure_isRecordedWithoutRefire() {
        givenMerged(Map.of("period", "DAILY", "topAuthors", "5"));
        when(reportService.generate(ReportPeriod.DAILY, 5)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> new CatalogReportJob(reportService).execute(context))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(ex -> {
                    JobExecutionException jee = (JobExecutionException) ex;
                    assertThat(jee.refireImmediately()).isFalse();
                    assertThat(jee.unscheduleAllTriggers()).isFalse();
                });
    }

    @Test
    void reportJob_isAnnotatedWithDisallowConcurrentExecution() {
        assertThat(CatalogReportJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
        assertThat(config.catalogReportJobDetail().isConcurrentExectionDisallowed()).isTrue();
    }
}

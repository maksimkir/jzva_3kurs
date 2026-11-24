package com.example.library.scheduling;

import com.example.library.report.CatalogReportService;
import com.example.library.report.ReportPeriod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Поведінка в РЕАЛЬНОМУ планувальнику Quartz (in-memory RAMJobStore, 3 потоки) — без Spring і без БД:
 * @DisallowConcurrentExecution і стратегія «збій -> без повтору» для звіту.
 */
class QuartzRuntimeBehaviourTest {

    private final QuartzJobsConfig config = new QuartzJobsConfig(
            new SchedulingProperties("Europe/Kyiv", "0 0 6 * * ?", "0 0 7 ? * MON"));
    private final CatalogReportService service = mock(CatalogReportService.class);
    private Scheduler scheduler;

    @BeforeEach
    void startScheduler() throws Exception {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "test-" + UUID.randomUUID());
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        scheduler = new StdSchedulerFactory(props).getScheduler();
        scheduler.setJobFactory((bundle, s) -> new CatalogReportJob(service)); // замість SpringBeanJobFactory
        scheduler.addJob(config.catalogReportJobDetail(), true);               // без тригерів за розкладом
        scheduler.start();
    }

    @AfterEach
    void stopScheduler() throws Exception {
        scheduler.shutdown(false);
    }

    @Test
    void reportJob_secondFireWhileFirstRunning_waitsUntilFirstFinishes() throws Exception {
        // Arrange: перший запуск «зависає», поки тест не відпустить
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothDone = new CountDownLatch(2);
        when(service.generate(any(ReportPeriod.class), anyInt())).thenAnswer(inv -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            firstStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            running.decrementAndGet();
            bothDone.countDown();
            return null;
        });

        // Act: два запуски одного завдання, вільних потоків — 3
        scheduler.triggerJob(QuartzJobsConfig.REPORT_JOB);
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.triggerJob(QuartzJobsConfig.REPORT_JOB);

        // Assert: другий тригер Quartz тримає у стані BLOCKED, доки триває перший
        assertThat(waitForBlockedTrigger()).as("second fire must be BLOCKED").isTrue();
        assertThat(running.get()).isEqualTo(1);

        release.countDown();
        assertThat(bothDone.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(maxRunning.get()).as("never two executions at the same time").isEqualTo(1);
        verify(service, times(2)).generate(ReportPeriod.DAILY, 5);
    }

    @Test
    void reportJob_failing_isRecordedOnceWithoutRefire() throws Exception {
        // Arrange: БД недоступна; стратегія звіту — без повторів
        when(service.generate(any(ReportPeriod.class), anyInt())).thenThrow(new IllegalStateException("db down"));
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch gaveUp = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new JobListener() {
            @Override
            public String getName() {
                return "test-listener";
            }

            @Override
            public void jobToBeExecuted(JobExecutionContext context) {
            }

            @Override
            public void jobExecutionVetoed(JobExecutionContext context) {
            }

            @Override
            public void jobWasExecuted(JobExecutionContext context, JobExecutionException ex) {
                executions.incrementAndGet();
                if (ex != null && !ex.refireImmediately()) {
                    gaveUp.countDown();
                }
            }
        }, KeyMatcher.keyEquals(QuartzJobsConfig.REPORT_JOB));

        // Act
        scheduler.triggerJob(QuartzJobsConfig.REPORT_JOB);

        // Assert: рівно 1 виконання без refire (чекаємо наступного запуску за розкладом)
        assertThat(gaveUp.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executions.get()).isEqualTo(1);
        verify(service, times(1)).generate(ReportPeriod.DAILY, 5);
    }

    /** Чекає (до 5 с), поки один із тригерів завдання перейде в BLOCKED. */
    private boolean waitForBlockedTrigger() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(QuartzJobsConfig.REPORT_JOB);
            for (Trigger t : triggers) {
                TriggerKey key = t.getKey();
                if (scheduler.getTriggerState(key) == Trigger.TriggerState.BLOCKED) {
                    return true;
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        return false;
    }
}

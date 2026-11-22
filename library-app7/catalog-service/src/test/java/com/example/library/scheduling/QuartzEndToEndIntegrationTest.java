package com.example.library.scheduling;

import com.example.library.report.ReportPeriod;
import com.example.library.repository.CatalogReportRepository;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end IT: запущений планувальник із JDBC store виконує CatalogReportJob через Spring
 * (SpringBeanJobFactory інжектить сервіс у конструктор), з параметрами з БД.
 */
@SpringBootTest(classes = QuartzItSupport.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QuartzEndToEndIntegrationTest {

    @DynamicPropertySource
    static void h2(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:lab6e2e;DB_CLOSE_DELAY=-1");
        for (String p : QuartzItSupport.H2_PROPERTIES) {
            String[] kv = p.split("=", 2);
            registry.add(kv[0], () -> kv[1]);
        }
    }

    @Autowired private Scheduler scheduler;
    @Autowired private CatalogReportRepository reports;

    @Test
    void reportJob_triggeredFromJdbcStore_runsThroughSpringAndStoresReport() throws Exception {
        // Arrange
        BlockingQueue<Object> results = new ArrayBlockingQueue<>(1);
        scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
            @Override
            public String getName() {
                return "e2e";
            }

            @Override
            public void jobWasExecuted(JobExecutionContext context, JobExecutionException ex) {
                results.offer(ex != null ? ex : "ok");
            }
        }, KeyMatcher.keyEquals(QuartzJobsConfig.REPORT_JOB));

        // Act: ручний запуск з перевизначенням (як тригер) — period=WEEKLY, topAuthors береться з JobDetail
        scheduler.triggerJob(QuartzJobsConfig.REPORT_JOB, new JobDataMap(Map.of("period", "WEEKLY")));

        // Assert
        assertThat(results.poll(10, TimeUnit.SECONDS)).isEqualTo("ok");
        assertThat(reports.findAll()).singleElement()
                .satisfies(r -> {
                    assertThat(r.getPeriod()).isEqualTo(ReportPeriod.WEEKLY);
                    assertThat(r.getTotalBooks()).isPositive();
                });
    }
}

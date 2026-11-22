package com.example.library.scheduling;

import com.example.library.report.CatalogReportService;
import com.example.library.report.ReportPeriod;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Set;

/**
 * ЛР6, завдання 2 — плановий звіт по каталогу. Має ДВА тригери: щоденний (параметри за замовчуванням)
 * і щотижневий (перевизначає period=WEEKLY і topAuthors=10).
 * <p>
 * Стратегія збою: ФІКСАЦІЯ помилки й очікування наступного запуску за розкладом (без повторів) —
 * звіт не термінові дані, а повтор при, наприклад, недоступній БД лише створив би зайве навантаження.
 */
@DisallowConcurrentExecution
public class CatalogReportJob implements Job {

    public static final String PERIOD = "period";
    public static final String TOP_AUTHORS = "topAuthors";
    static final Set<String> KEYS = Set.of(PERIOD, TOP_AUTHORS);

    private final CatalogReportService reportService;

    public CatalogReportJob(CatalogReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobParams params = JobParams.of(context.getMergedJobDataMap(), "catalogReport", KEYS);
        ReportPeriod period = params.requiredEnum(PERIOD, ReportPeriod.class);
        int topAuthors = params.requiredInt(TOP_AUTHORS, 1);

        try {
            reportService.generate(period, topAuthors);
        } catch (RuntimeException e) {
            throw JobFailures.logAndWaitForNextRun(context, e);
        }
    }
}

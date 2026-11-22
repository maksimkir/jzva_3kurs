package com.example.library.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Стратегія обробки збою завдання звіту (повтор з обмеженням — у notification-service). */
@Slf4j
public final class JobFailures {

    private JobFailures() {
    }

    /** Без повторів: фіксуємо помилку, наступний запуск — за розкладом. */
    public static JobExecutionException logAndWaitForNextRun(JobExecutionContext context, Exception cause) {
        log.error("[quartz] {} failed, waiting for the next scheduled run", context.getJobDetail().getKey(), cause);
        return new JobExecutionException(cause, false);
    }
}

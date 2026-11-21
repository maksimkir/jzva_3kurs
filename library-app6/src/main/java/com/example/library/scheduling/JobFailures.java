package com.example.library.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Дві явні стратегії обробки збою завдання. Обидві завершуються JobExecutionException — Quartz фіксує помилку. */
@Slf4j
public final class JobFailures {

    private JobFailures() {
    }

    /**
     * Повтор з обмеженням: поки {@code refireCount < maxRetries} — refireImmediately=true;
     * після вичерпання спроб — помилка в лозі і refire=false (чекаємо наступного запуску за розкладом).
     */
    public static JobExecutionException retryWithLimit(JobExecutionContext context, Exception cause, int maxRetries) {
        int attempt = context.getRefireCount() + 1;
        String job = context.getJobDetail().getKey().toString();
        if (context.getRefireCount() < maxRetries) {
            log.warn("[quartz] {} failed on attempt {}/{} ({}), refiring immediately",
                    job, attempt, maxRetries + 1, cause.toString());
            return new JobExecutionException(cause, true);
        }
        log.error("[quartz] {} failed after {} attempt(s), giving up until the next scheduled run",
                job, attempt, cause);
        return new JobExecutionException(cause, false);
    }

    /** Без повторів: фіксуємо помилку, наступний запуск — за розкладом. */
    public static JobExecutionException logAndWaitForNextRun(JobExecutionContext context, Exception cause) {
        log.error("[quartz] {} failed, waiting for the next scheduled run", context.getJobDetail().getKey(), cause);
        return new JobExecutionException(cause, false);
    }
}

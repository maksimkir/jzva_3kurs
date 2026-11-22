package com.example.notification.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Стратегія обробки збою завдання дайджесту. Завершується JobExecutionException — Quartz фіксує помилку. */
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

}

package com.example.library.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Advice: {@code @Around}. Викликає {@code proceed()} повторно, поки метод не завершиться успішно,
 * не вичерпаються спроби або не виникне виняток, який не вважається тимчасовим.
 * <p>
 * Порядок: аспект має вищий пріоритет, ніж {@code @Transactional} (у того LOWEST_PRECEDENCE),
 * тобто знаходиться ЗОВНІ транзакції — кожна спроба виконується в новій транзакції,
 * а не в уже «зламаній» (rollback-only).
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RetryOnTransientFailureAspect {

    /** Абстракція паузи — щоб unit-тести не використовували реальний Thread.sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Sleeper sleeper;

    public RetryOnTransientFailureAspect() {
        this(Thread::sleep);
    }

    RetryOnTransientFailureAspect(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Around("@annotation(com.example.library.aop.RetryOnTransientFailure)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return retry(joinPoint, AopAnnotations.find(joinPoint, RetryOnTransientFailure.class));
    }

    /** Логіка рішень advice (окремим методом — щоб unit-тест передавав будь-які атрибути анотації). */
    Object retry(ProceedingJoinPoint joinPoint, RetryOnTransientFailure retry) throws Throwable {
        int maxAttempts = Math.max(1, retry.maxAttempts());
        String method = joinPoint.getSignature().toShortString();

        for (int attempt = 1; ; attempt++) {
            try {
                Object result = joinPoint.proceed();
                if (attempt > 1) {
                    log.info("[retry] {} succeeded on attempt {}/{}", method, attempt, maxAttempts);
                }
                return result;
            } catch (Throwable ex) {
                if (!isRetryable(ex, retry.retryOn())) {
                    throw ex; // бізнес-помилка (404, валідація...) — повтор не допоможе
                }
                if (attempt >= maxAttempts) {
                    log.error("[retry] {} failed after {} attempt(s), giving up: {}", method, attempt, ex.toString());
                    throw ex;
                }
                long delay = retry.backoffMs() * attempt;
                log.warn("[retry] {} failed on attempt {}/{} ({}), retrying in {} ms",
                        method, attempt, maxAttempts, ex.getClass().getSimpleName(), delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    static boolean isRetryable(Throwable ex, Class<? extends Throwable>[] retryOn) {
        for (Class<? extends Throwable> type : retryOn) {
            if (type.isInstance(ex)) {
                return true;
            }
        }
        return false;
    }
}

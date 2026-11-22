package com.example.library.aop;

import org.springframework.dao.TransientDataAccessException;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ЛР4, анотація 1 — повтор операції при тимчасовому (transient) збої.
 * <p>
 * Наскрізна задача: deadlock / lock timeout / короткочасна втрата з'єднання з БД
 * не повинні одразу перетворюватись на 500 для користувача — операцію варто
 * повторити кілька разів із паузою. Без AOP той самий цикл try/catch довелося б
 * копіювати в кожен метод запису.
 * <p>
 * Обробляє {@link RetryOnTransientFailureAspect} ({@code @Around}).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryOnTransientFailure {

    /** Загальна кількість спроб (разом з першою). */
    int maxAttempts() default 3;

    /** Базова пауза між спробами; перед спробою N+1 чекаємо backoffMs * N (лінійний backoff). */
    long backoffMs() default 100;

    /** Які винятки вважаються тимчасовими (повторюються). Решта пробрасується одразу. */
    Class<? extends Throwable>[] retryOn() default {TransientDataAccessException.class};
}

package com.example.library.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ЛР4, анотація 3 — обмеження частоти викликів (rate limiting) для кожного користувача.
 * <p>
 * Наскрізна задача: захист «дорогих» або чутливих ендпоінтів від зловживань і випадкового спаму
 * (скрипт у циклі, подвійні кліки). Дозволено не більше {@link #permits()} викликів
 * за {@link #windowSeconds()} секунд для одного користувача і одного методу.
 * <p>
 * {@link RateLimitedAspect} ({@code @Before}) кидає {@link RateLimitExceededException}
 * (-> HTTP 429 Too Many Requests + заголовок Retry-After), і метод не виконується.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /** Скільки викликів дозволено в одному вікні. */
    int permits();

    /** Довжина вікна в секундах. */
    long windowSeconds() default 60;
}

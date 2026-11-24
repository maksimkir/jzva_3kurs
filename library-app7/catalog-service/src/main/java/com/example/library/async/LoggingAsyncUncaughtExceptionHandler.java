package com.example.library.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ЛР5 — фіксує винятки з @Async-методів, що повертають void (fire-and-forget).
 * У таких методів немає Future, куди можна «покласти» помилку, тому без цього обробника вона
 * зникла б непомітно. Тут вона потрапляє в лог рівня ERROR зі стеком і рахується в лічильнику.
 */
@Slf4j
@Component
public class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final AtomicLong failures = new AtomicLong();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        long total = failures.incrementAndGet();
        log.error("[async] fire-and-forget task {}.{} failed (params={}, total failures={}): {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), Arrays.toString(params), total,
                ex.toString(), ex);
    }

    /** Скільки фонових завдань завершилось помилкою з моменту старту. */
    public long getFailureCount() {
        return failures.get();
    }
}

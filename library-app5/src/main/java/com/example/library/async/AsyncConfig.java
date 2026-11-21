package com.example.library.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ЛР5 — явно визначений виконавець з обмеженими ресурсами.
 * <ul>
 *   <li>core/max/queue/prefix задаються явно (application.yml -> {@link AsyncProperties});</li>
 *   <li>політика переповнення — AbortPolicy: зайве завдання НЕ виконується «як вийде», а кидає
 *       TaskRejectedException, яке код обробляє свідомо (503 «спробуйте пізніше» або пропуск сповіщення);</li>
 *   <li>бін має ім'я {@link #EXECUTOR}, і всі @Async-методи посилаються на нього явно, тому Spring Boot
 *       НЕ створює свій applicationTaskExecutor і НЕ використовується SimpleAsyncTaskExecutor;</li>
 *   <li>помилки @Async void-методів фіксує {@link LoggingAsyncUncaughtExceptionHandler}.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String EXECUTOR = "libraryTaskExecutor";

    private final AsyncProperties properties;
    private final LoggingAsyncUncaughtExceptionHandler uncaughtExceptionHandler;

    public AsyncConfig(AsyncProperties properties, LoggingAsyncUncaughtExceptionHandler uncaughtExceptionHandler) {
        this.properties = properties;
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor libraryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.coreSize());
        executor.setMaxPoolSize(properties.maxSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true); // не губимо листи при зупинці застосунку
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** Виконавець за замовчуванням для @Async без кваліфікатора — теж наш пул. */
    @Override
    public Executor getAsyncExecutor() {
        return libraryTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }
}

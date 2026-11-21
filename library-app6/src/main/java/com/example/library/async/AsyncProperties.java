package com.example.library.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ЛР5 — налаштування власного пулу потоків і тайм-аутів (application.yml, префікс {@code library.async}).
 *
 * @param coreSize         базова кількість потоків
 * @param maxSize          максимальна кількість потоків (створюються, лише коли черга заповнена)
 * @param queueCapacity    місткість черги завдань; коли і черга, і maxSize вичерпані -> TaskRejectedException
 * @param threadNamePrefix префікс назви потоків — по ньому видно в логах, що працює НАШ виконавець
 * @param callTimeoutMs    максимальний час очікування одного зовнішнього виклику
 * @param reportTimeoutMs  максимальний час очікування об'єднаного результату (звіту)
 */
@ConfigurationProperties(prefix = "library.async")
public record AsyncProperties(
        int coreSize,
        int maxSize,
        int queueCapacity,
        String threadNamePrefix,
        long callTimeoutMs,
        long reportTimeoutMs) {
}

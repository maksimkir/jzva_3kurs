package com.example.library.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ЛР7 — адреси інших сервісів (application.yml, префікс {@code library.services}).
 * Сервіси знають один про одного лише URL-адресу і DTO з модуля common — Maven-залежності між ними немає.
 *
 * @param notificationUrl  базова адреса notification-service
 * @param notificationApiKey ключ для внутрішнього API notification-service (заголовок X-Internal-Api-Key)
 * @param bookInfoUrl      базова адреса book-info-service
 * @param connectTimeoutMs тайм-аут з'єднання
 * @param readTimeoutMs    тайм-аут читання відповіді
 */
@ConfigurationProperties(prefix = "library.services")
public record ServiceClientsProperties(
        String notificationUrl,
        String notificationApiKey,
        String bookInfoUrl,
        int connectTimeoutMs,
        int readTimeoutMs) {
}

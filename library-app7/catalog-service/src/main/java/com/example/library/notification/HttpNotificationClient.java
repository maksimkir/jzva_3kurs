package com.example.library.notification;

import com.example.library.common.notification.BookCreatedNotification;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** ЛР7 — HTTP-реалізація: {@code POST {notification-service}/internal/notifications/book-created}. */
@Component
public class HttpNotificationClient implements NotificationClient {

    private final RestClient client;

    public HttpNotificationClient(@Qualifier("notificationRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public void bookCreated(BookCreatedNotification notification) {
        client.post()
                .uri("/internal/notifications/book-created")
                .body(notification)
                .retrieve()
                .toBodilessEntity(); // 4xx/5xx -> виняток -> фіксує LoggingAsyncUncaughtExceptionHandler
    }
}

package com.example.library.notification;

import com.example.library.common.notification.BookCreatedNotification;

/** ЛР7 — відправка повідомлень у notification-service (окремий сервіс). Інтерфейс — щоб тести могли підмінити HTTP. */
public interface NotificationClient {

    void bookCreated(BookCreatedNotification notification);
}

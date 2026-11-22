package com.example.library.common.notification;

/**
 * ЛР7: спільний DTO (common) — повідомлення «створено нову книгу».
 * catalog-service надсилає його в notification-service ({@code POST /internal/notifications/book-created}),
 * той надсилає лист підписникам і ставить книгу в чергу дайджесту новинок.
 *
 * @param bookId id книги в catalog-service (ключ ідемпотентності: повторне повідомлення не дублює дайджест)
 */
public record BookCreatedNotification(Long bookId, String title, String author) {
}

package com.example.library.notification;

import com.example.library.async.AsyncConfig;
import com.example.library.common.notification.BookCreatedNotification;
import com.example.library.dto.BookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * ЛР5 + ЛР7 — повідомлення про нову книгу, «запустив і забув» (fire-and-forget).
 * <p>
 * ЛР7: лист більше не надсилається тут — це робить окремий notification-service. catalog-service лише
 * передає йому {@link BookCreatedNotification} (DTO з модуля common) по HTTP. Виклик лишився @Async:
 * create() не чекає на інший сервіс, а якщо той недоступний — помилку фіксує LoggingAsyncUncaughtExceptionHandler.
 * <p>
 * Окремий бін (а не метод BookService), щоб виклик ішов через проксі — інакше @Async не спрацював би (ЛР4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookNotificationService {

    private final NotificationClient notificationClient;

    @Async(AsyncConfig.EXECUTOR)
    public void notifyNewBook(BookDto book) {
        log.info("[notify] sending 'book created' for id={} to notification-service in thread {}",
                book.getId(), Thread.currentThread().getName());
        notificationClient.bookCreated(new BookCreatedNotification(book.getId(), book.getTitle(), book.getAuthor()));
    }
}

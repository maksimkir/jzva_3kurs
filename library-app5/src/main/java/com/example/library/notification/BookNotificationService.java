package com.example.library.notification;

import com.example.library.async.AsyncConfig;
import com.example.library.dto.BookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * ЛР5, повільний побічний ефект №1 — лист читачам-підписникам про нову книгу.
 * Сценарій «запустив і забув» (fire-and-forget): метод повертає void, тож BookService.create
 * не чекає ~1 с на SMTP — керування повертається одразу, лист іде в потоці «library-async-*».
 * Помилки цього методу фіксує LoggingAsyncUncaughtExceptionHandler.
 * <p>
 * Окремий бін (а не метод BookService), щоб виклик ішов через проксі — інакше @Async не спрацював би
 * (та сама проблема self-invocation, що й у ЛР4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookNotificationService {

    private final MailGateway mailGateway;

    @Value("${library.notifications.subscribers:readers@library.local}")
    private String subscribers;

    @Async(AsyncConfig.EXECUTOR)
    public void notifyNewBook(BookDto book) {
        log.info("[notify] sending 'new book' email for id={} in thread {}", book.getId(), Thread.currentThread().getName());
        mailGateway.send(subscribers,
                "Нова книга в бібліотеці: " + book.getTitle(),
                "«" + book.getTitle() + "» — " + book.getAuthor() + ". Чекаємо на вас!");
    }
}

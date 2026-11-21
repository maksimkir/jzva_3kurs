package com.example.library.async;

import com.example.library.async.AsyncTestBeans.ControllableLatency;
import com.example.library.async.AsyncTestBeans.RecordingMailGateway;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.notification.BookNotificationService;
import com.example.library.report.BookReportService;
import com.example.library.repository.BookRepository;
import com.example.library.service.BookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * ЛР5 — переповнення виконавця. Пул СПЕЦІАЛЬНО крихітний: 1 потік + черга на 1 завдання.
 * Третє одночасне завдання відхиляється (TaskRejectedException), і кожне місце виклику обробляє це свідомо.
 */
@SpringJUnitConfig(ExecutorRejectionTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // свіжий порожній пул для кожного тесту
class ExecutorRejectionTest {

    @Configuration
    @Import(AsyncTestBeans.class)
    static class Config {
        @Bean
        AsyncProperties asyncProperties() {
            return new AsyncProperties(1, 1, 1, "tiny-async-", 500, 1000);
        }
    }

    @Autowired private BookNotificationService notifications;
    @Autowired private BookService bookService;
    @Autowired private BookReportService reportService;
    @Autowired private BookRepository repository;
    @Autowired private RecordingMailGateway mail;
    @Autowired private ControllableLatency latency;

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseBlockedThreads() {
        release.countDown();
    }

    private static BookDto book() {
        BookDto dto = new BookDto();
        dto.setId(1L);
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        return dto;
    }

    /** Займаємо єдиний потік і єдине місце в черзі «зависаючими» листами. */
    private void fillExecutor() {
        mail.gate = release;
        notifications.notifyNewBook(book()); // 1 — виконується (і чекає)
        notifications.notifyNewBook(book()); // 2 — у черзі
    }

    @Test
    void notifyNewBook_executorFull_thirdTaskIsRejected() {
        // Arrange
        fillExecutor();

        // Act + Assert: «сира» поведінка виконавця — відмова, а не необмежене накопичення
        assertThatThrownBy(() -> notifications.notifyNewBook(book())).isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void create_executorFull_bookIsCreatedAndNotificationSkippedWithoutError() {
        // Arrange
        fillExecutor();

        // Act + Assert: явне рішення №1 — книга створюється, лист пропускається (WARN у лозі), не 500
        assertThatCode(() -> {
            BookDto created = bookService.create(book());
            assertThat(created.getId()).isEqualTo(1L);
        }).doesNotThrowAnyException();
    }

    @Test
    void report_executorFull_rejectionPropagatesForTryLaterResponse() {
        // Arrange
        Book entity = new Book();
        entity.setId(1L);
        entity.setTitle("Clean Code");
        entity.setAuthor("Robert C. Martin");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        latency.behavior = op -> { };
        fillExecutor();

        // Act + Assert: явне рішення №2 — TaskRejectedException іде до GlobalExceptionHandler -> 503 «спробуйте пізніше»
        // (сам 503 перевіряє BookReportEndpointTest)
        assertThatThrownBy(() -> reportService.buildReport(1L)).isInstanceOf(TaskRejectedException.class);
    }
}

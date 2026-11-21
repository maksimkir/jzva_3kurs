package com.example.library.async;

import com.example.library.entity.Book;
import com.example.library.external.ExternalBookInfoClient;
import com.example.library.mapper.BookMapperImpl;
import com.example.library.notification.BookNotificationService;
import com.example.library.notification.MailGateway;
import com.example.library.report.BookReportService;
import com.example.library.repository.BookRepository;
import com.example.library.service.BookService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;

/**
 * Мінімальний Spring-контекст для тестів ЛР5: РЕАЛЬНИЙ AsyncConfig (@EnableAsync + наш ThreadPoolTaskExecutor),
 * реальні async-біни, а повільність і SMTP — керовані з тесту (latch/barrier замість Thread.sleep).
 * Без БД, Keycloak і @SpringBootTest. AsyncProperties кожен тестовий клас задає сам.
 */
@Configuration
@Import(AsyncConfig.class)
class AsyncTestBeans {

    /** Затримка, поведінку якої задає тест; запам'ятовує, в якому потоці виконувалась кожна операція. */
    static class ControllableLatency extends LatencySimulator {
        final Map<String, String> threads = new ConcurrentHashMap<>();
        volatile Consumer<String> behavior = op -> { };

        ControllableLatency() {
            super(0);
        }

        @Override
        public void simulate(String operation) {
            threads.put(operation, Thread.currentThread().getName());
            behavior.accept(operation);
        }
    }

    /** «SMTP», який тест може притримати (gate) або змусити впасти (fail). */
    static class RecordingMailGateway implements MailGateway {
        volatile CountDownLatch gate = new CountDownLatch(0);
        volatile CountDownLatch sent = new CountDownLatch(1);
        volatile boolean fail;
        volatile String thread;

        void reset() {
            gate = new CountDownLatch(0);
            sent = new CountDownLatch(1);
            fail = false;
            thread = null;
        }

        @Override
        public void send(String to, String subject, String body) {
            thread = Thread.currentThread().getName();
            AsyncTestBeans.await(gate);
            if (fail) {
                throw new IllegalStateException("SMTP server unavailable (test)");
            }
            sent.countDown();
        }
    }

    /** Обробник з «сигналом» для тесту: реальна логіка (лог + лічильник) + latch. */
    static class SignallingUncaughtHandler extends LoggingAsyncUncaughtExceptionHandler {
        volatile CountDownLatch handled = new CountDownLatch(1);
        volatile Throwable lastError;

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            super.handleUncaughtException(ex, method, params);
            lastError = ex;
            handled.countDown();
        }
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("test gate was not released in 5 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Bean
    SignallingUncaughtHandler uncaughtHandler() {
        return new SignallingUncaughtHandler();
    }

    @Bean
    ControllableLatency latency() {
        return new ControllableLatency();
    }

    @Bean
    RecordingMailGateway mailGateway() {
        return new RecordingMailGateway();
    }

    @Bean
    BookNotificationService bookNotificationService(MailGateway mailGateway) {
        return new BookNotificationService(mailGateway);
    }

    @Bean
    ExternalBookInfoClient externalBookInfoClient(LatencySimulator latency) {
        return new ExternalBookInfoClient(latency);
    }

    @Bean
    BookRepository bookRepository() {
        BookRepository repository = Mockito.mock(BookRepository.class);
        Mockito.when(repository.save(any(Book.class))).thenAnswer(inv -> {
            Book book = inv.getArgument(0);
            book.setId(1L);
            return book;
        });
        return repository;
    }

    @Bean
    BookService bookService(BookRepository repository, BookNotificationService notifications) {
        return new BookService(repository, new BookMapperImpl(), notifications);
    }

    @Bean
    BookReportService bookReportService(BookService bookService, ExternalBookInfoClient client, AsyncProperties properties) {
        return new BookReportService(bookService, client, properties);
    }
}

package com.example.library.async;

import com.example.library.async.AsyncTestBeans.ControllableLatency;
import com.example.library.async.AsyncTestBeans.RecordingMailGateway;
import com.example.library.async.AsyncTestBeans.SignallingUncaughtHandler;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.report.BookReportDto;
import com.example.library.report.BookReportService;
import com.example.library.repository.BookRepository;
import com.example.library.service.BookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ЛР5 — сценарії методички на реальному @EnableAsync і нашому ThreadPoolTaskExecutor.
 */
@SpringJUnitConfig(AsyncProcessingTest.Config.class)
class AsyncProcessingTest {

    static final String PREFIX = "library-async-";

    @Configuration
    @Import(AsyncTestBeans.class)
    static class Config {
        @Bean
        AsyncProperties asyncProperties() {
            return new AsyncProperties(4, 4, 10, PREFIX, 500, 2000);
        }
    }

    @Autowired private ApplicationContext context;
    @Autowired private BookService bookService;
    @Autowired private BookReportService reportService;
    @Autowired private BookRepository repository;
    @Autowired private ControllableLatency latency;
    @Autowired private RecordingMailGateway mail;
    @Autowired private SignallingUncaughtHandler uncaughtHandler;

    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        mail.reset();
        latency.threads.clear();
        latency.behavior = op -> { };
        uncaughtHandler.handled = new CountDownLatch(1);
    }

    @AfterEach
    void releaseBlockedThreads() {
        release.countDown();
    }

    private static BookDto book(String isbn) {
        BookDto dto = new BookDto();
        dto.setId(1L);
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        dto.setIsbn(isbn);
        return dto;
    }

    private void bookInRepository(String isbn) {
        Book entity = new Book();
        entity.setId(1L);
        entity.setTitle("Clean Code");
        entity.setAuthor("Robert C. Martin");
        entity.setIsbn(isbn);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
    }

    // ---------- Сценарій: виконавець явно визначений і має обмежені ресурси ----------
    @Test
    void executor_isExplicitThreadPoolWithBoundedResources() {
        // Act
        ThreadPoolTaskExecutor executor = context.getBean(AsyncConfig.EXECUTOR, ThreadPoolTaskExecutor.class);

        // Assert
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getQueueCapacity()).isEqualTo(10);
        assertThat(executor.getThreadNamePrefix()).isEqualTo(PREFIX);
    }

    // ---------- Сценарій: повільний метод більше не блокує викликаючий код (fire-and-forget) ----------
    @Test
    void create_returnsBeforeEmailIsSent_andEmailIsSentLaterInOwnExecutorThread() throws InterruptedException {
        // Arrange: «SMTP» не завершиться, поки тест його не відпустить
        mail.gate = release;

        // Act
        BookDto created = bookService.create(book(null));

        // Assert 1: create() вже повернув результат, а лист ще НЕ відправлено
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(mail.sent.getCount()).as("email must still be pending").isEqualTo(1);

        // Assert 2: лист завершується пізніше — у потоці НАШОГО виконавця
        release.countDown();
        assertThat(mail.sent.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(mail.thread)
                .startsWith(PREFIX)
                .isNotEqualTo(Thread.currentThread().getName())
                .doesNotStartWith("SimpleAsyncTaskExecutor")
                .doesNotStartWith("task-");
    }

    // ---------- Сценарій: два незалежні виклики справді виконуються одночасно ----------
    @Test
    void report_twoExternalCallsRunConcurrently_andResultsAreCombined() {
        // Arrange: бар'єр на 2 учасники — пройти його можна, ЛИШЕ якщо обидва виклики працюють одночасно.
        // Якби вони йшли послідовно, перший чекав би другого до тайм-ауту і звіт був би неповним.
        CyclicBarrier bothRunning = new CyclicBarrier(2);
        latency.behavior = op -> {
            try {
                bothRunning.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("calls did not overlap", e);
            }
        };
        bookInRepository("9780132350884");

        // Act
        BookReportDto report = reportService.buildReport(1L);

        // Assert
        assertThat(report.complete()).isTrue();
        assertThat(report.rating().available()).isTrue();
        assertThat(report.rating().average()).isBetween(3.0, 5.0);
        assertThat(report.availability().available()).isTrue();
        assertThat(report.book().getTitle()).isEqualTo("Clean Code");
        String ratingThread = latency.threads.get("ratings API");
        String availabilityThread = latency.threads.get("partner libraries API");
        assertThat(ratingThread).startsWith(PREFIX);
        assertThat(availabilityThread).startsWith(PREFIX).isNotEqualTo(ratingThread);
    }

    // ---------- Сценарій: невдалий асинхронний виклик не призводить до збою викликаючого коду ----------
    @Test
    void report_ratingCallFails_requestStillSucceedsWithFallback() {
        // Arrange: без ISBN сервіс рейтингів кидає виняток у фоновому потоці
        bookInRepository(null);

        // Act
        BookReportDto report = reportService.buildReport(1L);

        // Assert: жодного винятку назовні; рейтинг — fallback з поясненням, наявність — є
        assertThat(report.complete()).isFalse();
        assertThat(report.rating().available()).isFalse();
        assertThat(report.rating().error()).contains("ISBN");
        assertThat(report.availability().available()).isTrue();
    }

    @Test
    void notifyNewBook_asyncVoidFails_errorIsRecordedByUncaughtExceptionHandler() throws InterruptedException {
        // Arrange
        mail.fail = true;
        long failuresBefore = uncaughtHandler.getFailureCount();

        // Act: create() успішний — помилка відбувається пізніше, у фоні
        BookDto created = bookService.create(book(null));

        // Assert
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(uncaughtHandler.handled.await(2, TimeUnit.SECONDS)).as("handler must be called").isTrue();
        assertThat(uncaughtHandler.lastError).hasMessageContaining("SMTP server unavailable");
        assertThat(uncaughtHandler.getFailureCount()).isEqualTo(failuresBefore + 1);
    }

    // ---------- Сценарій: очікування завжди обмежене в часі ----------
    @Test
    void report_externalServiceHangs_callerStopsWaitingAfterTimeout() {
        // Arrange: сервіс рейтингів «завис» (не відповідає, поки тест не відпустить)
        latency.behavior = op -> {
            if (op.equals("ratings API")) {
                AsyncTestBeans.await(release);
            }
        };
        bookInRepository("9780132350884");

        // Act
        long started = System.nanoTime();
        BookReportDto report = reportService.buildReport(1L);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // Assert: чекали ~call-timeout (500 мс), а не до «відвисання» сервісу (5 с)
        assertThat(elapsedMs).isLessThan(2000);
        assertThat(report.rating().available()).isFalse();
        assertThat(report.rating().error()).contains("тайм-аут");
        assertThat(report.availability().available()).isTrue();
    }
}

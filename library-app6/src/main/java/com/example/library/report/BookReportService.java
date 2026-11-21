package com.example.library.report;

import com.example.library.async.AsyncProperties;
import com.example.library.dto.BookDto;
import com.example.library.external.AvailabilityInfo;
import com.example.library.external.ExternalBookInfoClient;
import com.example.library.external.RatingInfo;
import com.example.library.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ЛР5 — «результат, необхідний викликаючому коду».
 * <ol>
 *   <li>Обидва @Async-виклики стартують одразу, без очікування один одного.</li>
 *   <li>Кожен обмежений тайм-аутом ({@code orTimeout}) і має fallback ({@code exceptionally}) —
 *       помилка чи повільний сервіс дають «unavailable», а не 500.</li>
 *   <li>Результати об'єднуються через {@code thenCombine}.</li>
 *   <li>Очікування підсумку теж обмежене: {@code get(timeout)}; після нього — частковий звіт.</li>
 * </ol>
 * TaskRejectedException (пул переповнений) свідомо НЕ ловиться тут: GlobalExceptionHandler
 * перетворює його на 503 «спробуйте пізніше».
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookReportService {

    private final BookService bookService;
    private final ExternalBookInfoClient client;
    private final AsyncProperties properties;

    public BookReportDto buildReport(Long bookId) {
        long started = System.nanoTime();
        BookDto book = bookService.findById(bookId); // 404, якщо книги немає — ще до запуску фонових завдань

        CompletableFuture<RatingInfo> rating = client.fetchRating(book)
                .orTimeout(properties.callTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[report] rating for book {} unavailable: {}", bookId, describe(ex));
                    return RatingInfo.unavailable(describe(ex));
                });

        CompletableFuture<AvailabilityInfo> availability = client.fetchAvailability(book)
                .orTimeout(properties.callTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[report] availability for book {} unavailable: {}", bookId, describe(ex));
                    return AvailabilityInfo.unavailable(describe(ex));
                });

        CompletableFuture<BookReportDto> report = rating.thenCombine(availability,
                (r, a) -> new BookReportDto(book, r, a, r.available() && a.available(), elapsedMs(started)));

        try {
            return report.get(properties.reportTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            report.cancel(true);
            log.warn("[report] book {}: report not ready in {} ms, returning partial result", bookId, properties.reportTimeoutMs());
            return partial(book, "Звіт не встиг сформуватись за " + properties.reportTimeoutMs() + " мс", started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return partial(book, "Очікування звіту перервано", started);
        } catch (ExecutionException e) { // не очікується: обидві гілки мають exceptionally
            log.error("[report] book {}: unexpected failure", bookId, e.getCause());
            return partial(book, describe(e.getCause()), started);
        }
    }

    private static BookReportDto partial(BookDto book, String reason, long started) {
        return new BookReportDto(book, RatingInfo.unavailable(reason), AvailabilityInfo.unavailable(reason), false,
                elapsedMs(started));
    }

    static String describe(Throwable ex) {
        Throwable cause = (ex instanceof CompletionException || ex instanceof ExecutionException) && ex.getCause() != null
                ? ex.getCause() : ex;
        if (cause instanceof TimeoutException) {
            return "Зовнішній сервіс не відповів вчасно (тайм-аут)";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}

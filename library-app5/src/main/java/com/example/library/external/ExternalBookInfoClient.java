package com.example.library.external;

import com.example.library.async.AsyncConfig;
import com.example.library.async.LatencySimulator;
import com.example.library.dto.BookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * ЛР5, повільні побічні ефекти №2 і №3 — два НЕЗАЛЕЖНІ виклики зовнішніх сервісів (~1 с кожен).
 * Обидва методи — @Async і повертають CompletableFuture, тому викликаючий код (BookReportService)
 * запускає їх паралельно і об'єднує через thenCombine: разом ~1 с замість ~2 с.
 * <p>
 * Відповіді імітуються детерміновано (від ISBN/назви), щоб результат був відтворюваним.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalBookInfoClient {

    private final LatencySimulator latency;

    /** Сервіс рецензій шукає книгу за ISBN — без ISBN запит неможливий (виняток -> обробляється у викликаючому коді). */
    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<RatingInfo> fetchRating(BookDto book) {
        log.info("[external] rating lookup for '{}' in thread {}", book.getTitle(), Thread.currentThread().getName());
        latency.simulate("ratings API");
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            throw new IllegalArgumentException("Сервіс рейтингів шукає книги лише за ISBN, а в книги його немає");
        }
        int hash = Math.abs(book.getIsbn().hashCode());
        double average = 3.0 + (hash % 21) / 10.0;   // 3.0 .. 5.0
        int votes = 10 + hash % 990;
        return CompletableFuture.completedFuture(RatingInfo.of(average, votes));
    }

    /** Каталог бібліотек-партнерів шукає за назвою + автором. */
    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<AvailabilityInfo> fetchAvailability(BookDto book) {
        log.info("[external] availability lookup for '{}' in thread {}", book.getTitle(), Thread.currentThread().getName());
        latency.simulate("partner libraries API");
        int hash = Math.abs((book.getTitle() + "|" + book.getAuthor()).hashCode());
        int libraries = hash % 6;                    // 0 .. 5
        int copies = libraries == 0 ? 0 : libraries + hash % 7;
        return CompletableFuture.completedFuture(AvailabilityInfo.of(libraries, copies));
    }
}

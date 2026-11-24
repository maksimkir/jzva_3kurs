package com.example.library.external;

import com.example.library.async.AsyncConfig;
import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.dto.BookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * ЛР5, повільні побічні ефекти №2 і №3 — два НЕЗАЛЕЖНІ виклики зовнішнього сервісу (~1 с кожен).
 * Обидва методи — @Async і повертають CompletableFuture, тому викликаючий код (BookReportService)
 * запускає їх паралельно і об'єднує через thenCombine: разом ~1 с замість ~2 с.
 * <p>
 * ЛР7: «зовнішній сервіс» тепер справжній окремий процес — book-info-service (порт 8082);
 * HTTP-виклик робить {@link BookInfoApi}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalBookInfoClient {

    private final BookInfoApi api;

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<RatingInfo> fetchRating(BookDto book) {
        log.info("[external] rating lookup for '{}' in thread {}", book.getTitle(), Thread.currentThread().getName());
        return CompletableFuture.completedFuture(api.rating(book));
    }

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<AvailabilityInfo> fetchAvailability(BookDto book) {
        log.info("[external] availability lookup for '{}' in thread {}", book.getTitle(), Thread.currentThread().getName());
        return CompletableFuture.completedFuture(api.availability(book));
    }
}

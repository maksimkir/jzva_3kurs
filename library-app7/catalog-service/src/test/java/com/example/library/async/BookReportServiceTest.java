package com.example.library.async;

import com.example.library.dto.BookDto;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.external.ExternalBookInfoClient;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.report.BookReportDto;
import com.example.library.report.BookReportService;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести логіки об'єднання і fallback-ів BookReportService: без Spring, клієнт замоканий,
 * future-и — вже завершені / завершені з помилкою / ніколи не завершені.
 */
@ExtendWith(MockitoExtension.class)
class BookReportServiceTest {

    @Mock private BookService bookService;
    @Mock private ExternalBookInfoClient client;

    private BookReportService service(long callTimeoutMs, long reportTimeoutMs) {
        return new BookReportService(bookService, client,
                new AsyncProperties(1, 1, 1, "t-", callTimeoutMs, reportTimeoutMs));
    }

    private BookDto book() {
        BookDto dto = new BookDto();
        dto.setId(3L);
        dto.setTitle("Refactoring");
        dto.setAuthor("Martin Fowler");
        when(bookService.findById(3L)).thenReturn(dto);
        return dto;
    }

    @Test
    void buildReport_bothCallsSucceed_combinesIntoCompleteReport() {
        // Arrange
        BookDto book = book();
        when(client.fetchRating(book)).thenReturn(CompletableFuture.completedFuture(RatingInfo.of(4.5, 120)));
        when(client.fetchAvailability(book)).thenReturn(CompletableFuture.completedFuture(AvailabilityInfo.of(3, 7)));

        // Act
        BookReportDto report = service(1000, 2000).buildReport(3L);

        // Assert
        assertThat(report.complete()).isTrue();
        assertThat(report.book()).isSameAs(book);
        assertThat(report.rating()).isEqualTo(RatingInfo.of(4.5, 120));
        assertThat(report.availability()).isEqualTo(AvailabilityInfo.of(3, 7));
    }

    @Test
    void buildReport_availabilityFails_usesFallbackAndMarksIncomplete() {
        // Arrange
        BookDto book = book();
        when(client.fetchRating(book)).thenReturn(CompletableFuture.completedFuture(RatingInfo.of(4.0, 10)));
        when(client.fetchAvailability(book)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("catalog down")));

        // Act
        BookReportDto report = service(1000, 2000).buildReport(3L);

        // Assert
        assertThat(report.complete()).isFalse();
        assertThat(report.rating().available()).isTrue();
        assertThat(report.availability()).isEqualTo(AvailabilityInfo.unavailable("catalog down"));
    }

    @Test
    void buildReport_overallWaitExceeded_returnsPartialReportInsteadOfHanging() {
        // Arrange: обидва future ніколи не завершаться; тайм-аут звіту (100 мс) менший за тайм-аут викликів
        BookDto book = book();
        when(client.fetchRating(book)).thenReturn(new CompletableFuture<>());
        when(client.fetchAvailability(book)).thenReturn(new CompletableFuture<>());

        // Act
        BookReportDto report = service(10_000, 100).buildReport(3L);

        // Assert
        assertThat(report.complete()).isFalse();
        assertThat(report.rating().error()).contains("не встиг");
        assertThat(report.availability().error()).contains("не встиг");
    }

    @Test
    void buildReport_unknownBook_throwsNotFoundWithoutStartingExternalCalls() {
        // Arrange
        when(bookService.findById(99L)).thenThrow(new ResourceNotFoundException("Book with id 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> service(1000, 2000).buildReport(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(client);
    }
}

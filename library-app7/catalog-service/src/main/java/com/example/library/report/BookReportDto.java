package com.example.library.report;

import com.example.library.dto.BookDto;
import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;

/**
 * Підсумковий результат, зібраний з двох паралельних зовнішніх викликів.
 * {@code complete=false} — частину даних отримати не вдалося (помилка / тайм-аут), але запит НЕ падає.
 */
public record BookReportDto(BookDto book, RatingInfo rating, AvailabilityInfo availability, boolean complete,
                            long elapsedMs) {
}

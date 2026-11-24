package com.example.bookinfo;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Відповіді «зовнішніх» сервісів, детерміновані (від ISBN / назви + автора), щоб результат був відтворюваним.
 * Логіка перенесена з ExternalBookInfoClient ЛР5 — тепер це окремий процес.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookInfoService {

    private final LatencySimulator latency;

    public RatingInfo rating(String isbn) {
        latency.simulate();
        if (isbn == null || isbn.isBlank()) {
            throw new MissingIsbnException();
        }
        int hash = Math.abs(isbn.hashCode());
        double average = 3.0 + (hash % 21) / 10.0;   // 3.0 .. 5.0
        int votes = 10 + hash % 990;
        log.info("[book-info] rating for isbn {} -> {} ({} votes)", isbn, average, votes);
        return RatingInfo.of(average, votes);
    }

    public AvailabilityInfo availability(String title, String author) {
        latency.simulate();
        int hash = Math.abs((title + "|" + author).hashCode());
        int libraries = hash % 6;                    // 0 .. 5
        int copies = libraries == 0 ? 0 : libraries + hash % 7;
        log.info("[book-info] availability for '{}' -> {} libraries, {} copies", title, libraries, copies);
        return AvailabilityInfo.of(libraries, copies);
    }
}

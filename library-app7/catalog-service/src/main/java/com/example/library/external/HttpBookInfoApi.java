package com.example.library.external;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.dto.BookDto;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * ЛР7 — HTTP-реалізація: book-info-service ({@code GET /api/book-info/rating}, {@code /availability}).
 * Відповідь 4xx (наприклад, 422 «книга без ISBN») перетворюється на виняток з поясненням сервісу —
 * далі його обробляє {@code exceptionally} у BookReportService.
 */
@Component
public class HttpBookInfoApi implements BookInfoApi {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public HttpBookInfoApi(@Qualifier("bookInfoRestClient") RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public RatingInfo rating(BookDto book) {
        return client.get()
                .uri(b -> b.path("/api/book-info/rating").queryParamIfPresent("isbn", Optional.ofNullable(book.getIsbn())).build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    ProblemDetail problem = objectMapper.readValue(res.getBody(), ProblemDetail.class);
                    throw new IllegalArgumentException(problem.getDetail());
                })
                .body(RatingInfo.class);
    }

    @Override
    public AvailabilityInfo availability(BookDto book) {
        return client.get()
                .uri(b -> b.path("/api/book-info/availability")
                        .queryParam("title", book.getTitle())
                        .queryParam("author", book.getAuthor())
                        .build())
                .retrieve()
                .body(AvailabilityInfo.class);
    }
}

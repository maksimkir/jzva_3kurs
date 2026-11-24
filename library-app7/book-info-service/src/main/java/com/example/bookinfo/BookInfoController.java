package com.example.bookinfo;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Публічне API (імітація стороннього сервісу) — без автентифікації. Відповіді — DTO з модуля common. */
@RestController
@RequestMapping("/api/book-info")
@RequiredArgsConstructor
public class BookInfoController {

    private final BookInfoService service;

    @GetMapping("/rating")
    public RatingInfo rating(@RequestParam(required = false) String isbn) {
        return service.rating(isbn);
    }

    @GetMapping("/availability")
    public AvailabilityInfo availability(@RequestParam String title, @RequestParam String author) {
        return service.availability(title, author);
    }

    /** 422: запит зрозумілий, але виконати його неможливо (немає ISBN) — з поясненням для catalog-service. */
    @ExceptionHandler(MissingIsbnException.class)
    public ProblemDetail missingIsbn(MissingIsbnException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}

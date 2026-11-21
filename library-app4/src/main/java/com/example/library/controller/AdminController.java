package com.example.library.controller;

import com.example.library.aop.RateLimited;
import com.example.library.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Acceptance criteria 6: шлях /api/admin/** обмежений у SecurityFilterChain (hasRole ADMIN).
 * Тут жодних анотацій безпеки немає — захист працює суто на рівні фільтра.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BookService bookService;

    /** ЛР4: не більше 5 запитів статистики на хвилину для одного адміністратора (інакше 429). */
    @RateLimited(permits = 5, windowSeconds = 60)
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("totalBooks", bookService.count());
    }
}

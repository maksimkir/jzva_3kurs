package com.example.notification.api;

import com.example.library.common.notification.BookCreatedNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** ЛР7 — внутрішнє API для catalog-service (захищене ключем, див. InternalApiKeyFilter). */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final BookCreatedService service;

    /** 202 — прийнято (новий або дубль; дубль нічого не змінює — ідемпотентно). 400 — неповні дані. */
    @PostMapping("/book-created")
    public ResponseEntity<Map<String, String>> bookCreated(@RequestBody BookCreatedNotification notification) {
        if (notification.bookId() == null || isBlank(notification.title()) || isBlank(notification.author())) {
            return ResponseEntity.badRequest().body(Map.of("detail", "bookId, title and author are required"));
        }
        BookCreatedService.Result result = service.onBookCreated(notification);
        return ResponseEntity.accepted().body(Map.of("bookId", String.valueOf(notification.bookId()), "result", result.name()));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

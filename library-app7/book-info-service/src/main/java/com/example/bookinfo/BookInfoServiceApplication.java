package com.example.bookinfo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ЛР7: власний main-клас book-info-service (порт 8082). */
@SpringBootApplication
public class BookInfoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookInfoServiceApplication.class, args);
    }
}

package com.example.bookinfo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Сервіс справді стартує як окремий Spring Boot застосунок і відповідає по HTTP (випадковий порт, затримка 0). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "book-info.latency-ms=0")
class BookInfoServiceApplicationTest {

    @Autowired private TestRestTemplate rest;
    @LocalServerPort private int port;

    @Test
    void contextStarts_andServesRating() {
        String body = rest.getForObject("http://localhost:" + port + "/api/book-info/rating?isbn=9780132350884", String.class);

        assertThat(body).contains("\"available\":true");
    }
}

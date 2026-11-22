package com.example.library.common.bookinfo;

/**
 * ЛР7: спільний DTO (common) — відповідь book-info-service, яку читає catalog-service.
 *
 * Рейтинг книги із зовнішнього сервісу рецензій.
 * {@code available=false} — сервіс не відповів / помилка; тоді {@code error} пояснює причину.
 */
public record RatingInfo(boolean available, Double average, Integer votes, String error) {

    public static RatingInfo of(double average, int votes) {
        return new RatingInfo(true, average, votes, null);
    }

    public static RatingInfo unavailable(String error) {
        return new RatingInfo(false, null, null, error);
    }
}

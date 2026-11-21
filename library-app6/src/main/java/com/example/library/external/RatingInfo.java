package com.example.library.external;

/**
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

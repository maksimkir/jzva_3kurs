package com.example.library.common.bookinfo;

/**
 * ЛР7: спільний DTO (common) — відповідь book-info-service, яку читає catalog-service.
 *
 * Наявність примірників у бібліотеках-партнерах (зовнішній каталог).
 * {@code available=false} — каталог не відповів / помилка.
 */
public record AvailabilityInfo(boolean available, Integer partnerLibraries, Integer copies, String error) {

    public static AvailabilityInfo of(int partnerLibraries, int copies) {
        return new AvailabilityInfo(true, partnerLibraries, copies, null);
    }

    public static AvailabilityInfo unavailable(String error) {
        return new AvailabilityInfo(false, null, null, error);
    }
}

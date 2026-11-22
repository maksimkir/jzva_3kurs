package com.example.library.scheduling;

import lombok.extern.slf4j.Slf4j;

import java.util.TimeZone;

/**
 * Явне визначення часового пояса для cron-тригерів.
 * <p>
 * Підводний камінь: {@code TimeZone.getTimeZone("Europe/Kyev")} (помилка в назві) НЕ кидає виняток,
 * а повертає GMT — і звіт «о 6:00» почне виходити о 8:00/9:00 за Києвом. Тому тут така підміна
 * хоча б фіксується в лозі як WARN, а тест перевіряє і правильний ідентифікатор, і цю поведінку.
 */
@Slf4j
public final class QuartzTimeZones {

    private QuartzTimeZones() {
    }

    public static TimeZone resolve(String id) {
        TimeZone zone = TimeZone.getTimeZone(id);
        if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(id)) {
            log.warn("[quartz] unknown time zone id '{}' — java.util.TimeZone fell back to GMT", id);
        }
        return zone;
    }
}

package com.example.library.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * ЛР6 — розклад завдань (application.yml, префікс {@code library.scheduling}).
 *
 * @param timeZone              часовий пояс cron-тригерів (НЕ залежить від пояса сервера)
 * @param dailyReportCron       щоденний звіт по каталогу
 * @param weeklyReportCron      щотижневий звіт по каталогу
 */
@ConfigurationProperties(prefix = "library.scheduling")
public record SchedulingProperties(
        String timeZone,
        String dailyReportCron,
        String weeklyReportCron) {

    /** Увага: невідомий ідентифікатор java.util.TimeZone мовчки замінює на GMT — див. {@link QuartzTimeZones}. */
    public TimeZone resolvedTimeZone() {
        return QuartzTimeZones.resolve(timeZone);
    }

    public ZoneId zoneId() {
        return resolvedTimeZone().toZoneId();
    }
}

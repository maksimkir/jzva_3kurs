package com.example.notification.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param digestIntervalMinutes інтервал дайджесту новинок (application.yml, {@code notification.scheduling}) */
@ConfigurationProperties(prefix = "notification.scheduling")
public record SchedulingProperties(int digestIntervalMinutes) {
}

package com.example.notification;

import com.example.notification.config.NotificationProperties;
import com.example.notification.scheduling.SchedulingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

/** ЛР7: власний main-клас notification-service (порт 8081). */
@SpringBootApplication
@EnableConfigurationProperties({NotificationProperties.class, SchedulingProperties.class})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // як і в catalog-service: PostgreSQL 16 не знає "Europe/Kiev"
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

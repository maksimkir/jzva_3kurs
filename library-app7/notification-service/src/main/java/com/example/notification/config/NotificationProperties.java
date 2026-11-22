package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param internalApiKey ключ, з яким інші сервіси викликають /internal/** (заголовок X-Internal-Api-Key)
 * @param subscribers    кому надсилаються листи
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String internalApiKey, String subscribers) {
}

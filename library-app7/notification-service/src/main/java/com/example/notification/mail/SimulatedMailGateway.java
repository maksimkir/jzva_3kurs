package com.example.notification.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Імітація SMTP: відправка триває ~latency-ms. {@code notification.simulation.mail-failure=true} — SMTP «недоступний»
 * (щоб показати повтори дайджесту з обмеженням).
 */
@Slf4j
@Component
public class SimulatedMailGateway implements MailGateway {

    private final long latencyMs;
    private final boolean failure;

    public SimulatedMailGateway(@Value("${notification.simulation.latency-ms:1000}") long latencyMs,
                                @Value("${notification.simulation.mail-failure:false}") boolean failure) {
        this.latencyMs = latencyMs;
        this.failure = failure;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SMTP send interrupted", e);
        }
        if (failure) {
            throw new IllegalStateException("SMTP server unavailable (simulated)");
        }
        log.info("[mail] sent '{}' to {}", subject, to);
    }
}

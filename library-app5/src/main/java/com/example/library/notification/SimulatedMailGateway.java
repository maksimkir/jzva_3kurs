package com.example.library.notification;

import com.example.library.async.LatencySimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Імітація SMTP: відправка триває ~1 с. Реальний поштовий сервер у лабі не потрібен.
 * {@code library.simulation.mail-failure=true} — імітувати недоступність SMTP (для демонстрації AsyncUncaughtExceptionHandler).
 */
@Slf4j
@Component
public class SimulatedMailGateway implements MailGateway {

    private final LatencySimulator latency;
    private final boolean failure;

    public SimulatedMailGateway(LatencySimulator latency,
                                @Value("${library.simulation.mail-failure:false}") boolean failure) {
        this.latency = latency;
        this.failure = failure;
    }

    @Override
    public void send(String to, String subject, String body) {
        latency.simulate("SMTP send to " + to);
        if (failure) {
            throw new IllegalStateException("SMTP server unavailable (simulated)");
        }
        log.info("[mail] sent '{}' to {} (thread {})", subject, to, Thread.currentThread().getName());
    }
}

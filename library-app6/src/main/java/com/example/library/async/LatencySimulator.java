package com.example.library.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Імітація «повільного» побічного ефекту (SMTP-сервер, зовнішній HTTP API) ~1 секунда.
 * Винесено в окремий бін, щоб тести могли підмінити затримку на керовану (latch) — без Thread.sleep у тестах.
 */
@Slf4j
@Component
public class LatencySimulator {

    private final long latencyMs;

    public LatencySimulator(@Value("${library.simulation.latency-ms:1000}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void simulate(String operation) {
        log.debug("[simulate] {} takes ~{} ms in thread {}", operation, latencyMs, Thread.currentThread().getName());
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " was interrupted", e);
        }
    }
}

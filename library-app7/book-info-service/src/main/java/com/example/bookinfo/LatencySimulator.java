package com.example.bookinfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Імітація повільного зовнішнього API (~1 с). У тестах затримка = 0 (властивість book-info.latency-ms). */
@Component
public class LatencySimulator {

    private final long latencyMs;

    public LatencySimulator(@Value("${book-info.latency-ms:1000}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void simulate() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}

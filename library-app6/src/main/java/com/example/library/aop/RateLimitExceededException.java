package com.example.library.aop;

import lombok.Getter;

/** Кидається {@link RateLimitedAspect}, коли ліміт викликів вичерпано. Мапиться на HTTP 429. */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

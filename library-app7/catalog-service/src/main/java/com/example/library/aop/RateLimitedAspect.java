package com.example.library.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advice: {@code @Before}. Алгоритм — фіксоване вікно (fixed window) на ключ «метод + користувач».
 * Якщо в поточному вікні викликів більше за permits — кидає {@link RateLimitExceededException},
 * тож цільовий метод не виконується. Стан зберігається в пам'яті одного інстансу застосунку
 * (для кластера знадобився б Redis/Bucket4j — тут цього свідомо не робимо).
 */
@Slf4j
@Aspect
@Component
public class RateLimitedAspect {

    static final String ANONYMOUS = "anonymous";

    /** Поточне вікно: секунда початку і кількість викликів у ньому. */
    record Window(long startEpochSecond, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitedAspect() {
        this(Clock.systemUTC());
    }

    /** Для тестів — керований годинник замість реального часу. */
    RateLimitedAspect(Clock clock) {
        this.clock = clock;
    }

    @Before("@annotation(com.example.library.aop.RateLimited)")
    public void before(JoinPoint joinPoint) {
        checkLimit(joinPoint, AopAnnotations.find(joinPoint, RateLimited.class));
    }

    void checkLimit(JoinPoint joinPoint, RateLimited limit) {
        String user = currentUser();
        String key = joinPoint.getSignature().toLongString() + "|" + user;
        long now = clock.instant().getEpochSecond();
        long windowSeconds = Math.max(1, limit.windowSeconds());

        Window window = windows.compute(key, (k, old) ->
                (old == null || now - old.startEpochSecond() >= windowSeconds)
                        ? new Window(now, 1)
                        : new Window(old.startEpochSecond(), old.count() + 1));

        if (window.count() > limit.permits()) {
            long retryAfter = windowSeconds - (now - window.startEpochSecond());
            String method = joinPoint.getSignature().toShortString();
            log.warn("[rate-limit] user '{}' exceeded {} call(s)/{}s on {}", user, limit.permits(), windowSeconds, method);
            throw new RateLimitExceededException(
                    "Забагато запитів до " + method + ": дозволено " + limit.permits() + " за " + windowSeconds
                            + " с. Спробуйте через " + retryAfter + " с.", retryAfter);
        }
    }

    static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated() && auth.getName() != null) ? auth.getName() : ANONYMOUS;
    }
}

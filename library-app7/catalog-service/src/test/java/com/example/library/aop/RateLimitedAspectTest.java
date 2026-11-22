package com.example.library.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit-тест @Before-advice: JoinPoint — mock, час — керований годинник (без Thread.sleep), без Spring-контексту.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitedAspectTest {

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RateLimitedAspect aspect = new RateLimitedAspect(clock);

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toLongString()).thenReturn("public java.util.Map AdminController.stats()");
        lenient().when(signature.toShortString()).thenReturn("AdminController.stats()");
        loginAs("admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkLimit_callsWithinPermits_allowed() throws Exception {
        // Arrange
        RateLimited limit = annotation("threePerMinute");

        // Act + Assert
        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> aspect.checkLimit(joinPoint, limit)).doesNotThrowAnyException();
        }
    }

    @Test
    void checkLimit_callOverPermits_throwsWithRetryAfter() throws Exception {
        // Arrange
        RateLimited limit = annotation("threePerMinute");
        for (int i = 0; i < 3; i++) {
            aspect.checkLimit(joinPoint, limit);
        }
        clock.advanceSeconds(20);

        // Act + Assert
        assertThatThrownBy(() -> aspect.checkLimit(joinPoint, limit))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("дозволено 3 за 60 с")
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    void checkLimit_windowExpired_counterResets() throws Exception {
        // Arrange
        RateLimited limit = annotation("threePerMinute");
        for (int i = 0; i < 3; i++) {
            aspect.checkLimit(joinPoint, limit);
        }
        clock.advanceSeconds(60);

        // Act + Assert
        assertThatCode(() -> aspect.checkLimit(joinPoint, limit)).doesNotThrowAnyException();
    }

    @Test
    void checkLimit_differentUsers_haveSeparateBuckets() throws Exception {
        // Arrange
        RateLimited limit = annotation("onePerMinute");
        aspect.checkLimit(joinPoint, limit);

        // Act: інший користувач — власний ліміт
        loginAs("user");

        // Assert
        assertThatCode(() -> aspect.checkLimit(joinPoint, limit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> aspect.checkLimit(joinPoint, limit)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkLimit_anonymousCaller_limitedUnderAnonymousKey() throws Exception {
        // Arrange
        SecurityContextHolder.clearContext();
        RateLimited limit = annotation("onePerMinute");
        aspect.checkLimit(joinPoint, limit);

        // Act + Assert
        assertThat(RateLimitedAspect.currentUser()).isEqualTo(RateLimitedAspect.ANONYMOUS);
        assertThatThrownBy(() -> aspect.checkLimit(joinPoint, limit)).isInstanceOf(RateLimitExceededException.class);
    }

    private static void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, "n/a", "ROLE_USER"));
    }

    private static RateLimited annotation(String method) throws NoSuchMethodException {
        return Samples.class.getDeclaredMethod(method).getAnnotation(RateLimited.class);
    }

    @SuppressWarnings("unused")
    private static class Samples {
        @RateLimited(permits = 3, windowSeconds = 60)
        void threePerMinute() {
        }

        @RateLimited(permits = 1)
        void onePerMinute() {
        }
    }

    /** Детермінований годинник, який тест «перемотує» вручну. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

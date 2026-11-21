package com.example.library.aop;

import com.example.library.exception.ResourceNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тест advice: ProceedingJoinPoint — mock, без Spring-контексту і без @SpringBootTest.
 * Перевіряється логіка рішень: скільки разів викликається proceed(), коли повторювати, коли здаватись.
 */
@ExtendWith(MockitoExtension.class)
class RetryOnTransientFailureAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    /** Замість Thread.sleep — запам'ятовуємо запитані паузи. */
    private final List<Long> sleeps = new ArrayList<>();
    private final RetryOnTransientFailureAspect aspect = new RetryOnTransientFailureAspect(sleeps::add);

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("BookService.create(..)");
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void retry_firstAttemptSucceeds_proceedsOnceAndDoesNotSleep() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("saved");

        // Act
        Object result = aspect.retry(joinPoint, annotation("defaults"));

        // Assert
        assertThat(result).isEqualTo("saved");
        verify(joinPoint, times(1)).proceed();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retry_transientFailureThenSuccess_retriesAndReturnsResult() throws Throwable {
        // Arrange
        when(joinPoint.proceed())
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenReturn("saved");

        // Act
        Object result = aspect.retry(joinPoint, annotation("defaults"));

        // Assert
        assertThat(result).isEqualTo("saved");
        verify(joinPoint, times(2)).proceed();
        assertThat(sleeps).containsExactly(100L);
    }

    @Test
    void retry_alwaysTransientFailure_givesUpAfterMaxAttemptsWithLinearBackoff() throws Throwable {
        // Arrange
        CannotAcquireLockException failure = new CannotAcquireLockException("lock timeout");
        when(joinPoint.proceed()).thenThrow(failure);

        // Act + Assert
        assertThatThrownBy(() -> aspect.retry(joinPoint, annotation("defaults"))).isSameAs(failure);
        verify(joinPoint, times(3)).proceed();
        assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test
    void retry_nonTransientFailure_rethrowsImmediatelyWithoutRetry() throws Throwable {
        // Arrange
        ResourceNotFoundException notFound = new ResourceNotFoundException("Book with id 1 not found");
        when(joinPoint.proceed()).thenThrow(notFound);

        // Act + Assert
        assertThatThrownBy(() -> aspect.retry(joinPoint, annotation("defaults"))).isSameAs(notFound);
        verify(joinPoint, times(1)).proceed();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retry_customRetryOnAndMaxAttempts_usesAnnotationAttributes() throws Throwable {
        // Arrange
        when(joinPoint.proceed())
                .thenThrow(new IllegalStateException("flaky"))
                .thenThrow(new IllegalStateException("flaky"))
                .thenThrow(new IllegalStateException("flaky"))
                .thenReturn(42);

        // Act
        Object result = aspect.retry(joinPoint, annotation("custom"));

        // Assert
        assertThat(result).isEqualTo(42);
        verify(joinPoint, times(4)).proceed();
        assertThat(sleeps).containsExactly(10L, 20L, 30L);
    }

    @Test
    void retry_maxAttemptsZero_treatedAsSingleAttempt() throws Throwable {
        // Arrange
        TransientDataAccessResourceException failure = new TransientDataAccessResourceException("connection reset");
        when(joinPoint.proceed()).thenThrow(failure);

        // Act + Assert
        assertThatThrownBy(() -> aspect.retry(joinPoint, annotation("zeroAttempts"))).isSameAs(failure);
        verify(joinPoint, times(1)).proceed();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retry_interruptedWhileWaiting_stopsRetryingAndKeepsInterruptFlag() throws Throwable {
        // Arrange
        RetryOnTransientFailureAspect interruptingAspect = new RetryOnTransientFailureAspect(ms -> {
            throw new InterruptedException();
        });
        CannotAcquireLockException failure = new CannotAcquireLockException("deadlock");
        when(joinPoint.proceed()).thenThrow(failure);

        // Act + Assert
        assertThatThrownBy(() -> interruptingAspect.retry(joinPoint, annotation("defaults"))).isSameAs(failure);
        verify(joinPoint, times(1)).proceed();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    // ---- анотації з різними атрибутами беремо з реальних методів ----
    private static RetryOnTransientFailure annotation(String method) throws NoSuchMethodException {
        return Samples.class.getDeclaredMethod(method).getAnnotation(RetryOnTransientFailure.class);
    }

    @SuppressWarnings("unused")
    private static class Samples {
        @RetryOnTransientFailure
        void defaults() {
        }

        @RetryOnTransientFailure(maxAttempts = 4, backoffMs = 10, retryOn = IllegalStateException.class)
        void custom() {
        }

        @RetryOnTransientFailure(maxAttempts = 0)
        void zeroAttempts() {
        }
    }
}

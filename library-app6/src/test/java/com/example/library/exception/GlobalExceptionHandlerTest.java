package com.example.library.exception;

import com.example.library.aop.RateLimitExceededException;
import com.example.library.dto.BookDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_resourceMissing_returns404WithMessage() {
        // Arrange
        ResourceNotFoundException ex = new ResourceNotFoundException("Book with id 5 not found");

        // Act
        ProblemDetail result = handler.handleNotFound(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getDetail()).isEqualTo("Book with id 5 not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleValidation_fieldErrors_returns400WithErrorsPerField() {
        // Arrange
        BindingResult bindingResult = new BeanPropertyBindingResult(new BookDto(), "book");
        bindingResult.addError(new FieldError("book", "title", "must not be blank"));
        bindingResult.addError(new FieldError("book", "author", "must not be blank"));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        // Act
        ProblemDetail result = handler.handleValidation(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getDetail()).isEqualTo("Validation failed");
        Map<String, String> errors = (Map<String, String>) result.getProperties().get("errors");
        assertThat(errors)
                .containsEntry("title", "must not be blank")
                .containsEntry("author", "must not be blank")
                .hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleValidation_noFieldErrors_returns400WithEmptyErrorsMap() {
        // Arrange
        BindingResult bindingResult = new BeanPropertyBindingResult(new BookDto(), "book");
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        // Act
        ProblemDetail result = handler.handleValidation(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat((Map<String, String>) result.getProperties().get("errors")).isEmpty();
    }

    @Test
    void handleRateLimit_limitExceeded_returns429WithRetryAfterHeader() {
        // Arrange
        RateLimitExceededException ex = new RateLimitExceededException("Забагато запитів", 42);

        // Act
        ResponseEntity<ProblemDetail> result = handler.handleRateLimit(ex);

        // Assert
        assertThat(result.getStatusCode().value()).isEqualTo(429);
        assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(result.getBody().getDetail()).isEqualTo("Забагато запитів");
    }
}

package com.example.library.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    void constructor_withMessage_keepsMessageAndIsRuntimeException() {
        // Arrange + Act
        ResourceNotFoundException ex = new ResourceNotFoundException("not here");

        // Assert
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("not here");
    }
}

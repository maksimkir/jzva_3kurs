package com.example.library.controller;

import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private AdminController controller;

    @Test
    void stats_booksExist_returnsTotalBooks() {
        // Arrange
        when(bookService.count()).thenReturn(7L);

        // Act
        Map<String, Object> result = controller.stats();

        // Assert
        assertThat(result).containsEntry("totalBooks", 7L);
        verify(bookService).count();
    }

    @Test
    void stats_emptyLibrary_returnsZero() {
        // Arrange
        when(bookService.count()).thenReturn(0L);

        // Act
        Map<String, Object> result = controller.stats();

        // Assert
        assertThat(result).containsEntry("totalBooks", 0L);
    }

    @Test
    void stats_serviceFails_propagatesException() {
        // Arrange
        when(bookService.count()).thenThrow(new IllegalStateException("db down"));

        // Act + Assert
        assertThatThrownBy(() -> controller.stats())
                .isInstanceOf(IllegalStateException.class);
    }
}

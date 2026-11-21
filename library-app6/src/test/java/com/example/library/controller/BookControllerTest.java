package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService service;

    @InjectMocks
    private BookController controller;

    private static BookDto dto(Long id, String title) {
        BookDto d = new BookDto();
        d.setId(id);
        d.setTitle(title);
        d.setAuthor("Author");
        return d;
    }

    @Test
    void list_booksExist_returnsServiceResult() {
        // Arrange
        List<BookDto> expected = List.of(dto(1L, "A"), dto(2L, "B"));
        when(service.findAll()).thenReturn(expected);

        // Act
        List<BookDto> result = controller.list();

        // Assert
        assertThat(result).containsExactlyElementsOf(expected);
    }

    @Test
    void list_noBooks_returnsEmptyList() {
        // Arrange
        when(service.findAll()).thenReturn(List.of());

        // Act
        List<BookDto> result = controller.list();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void get_existingId_returnsDto() {
        // Arrange
        BookDto expected = dto(1L, "A");
        when(service.findById(1L)).thenReturn(expected);

        // Act
        BookDto result = controller.get(1L);

        // Assert
        assertThat(result).isSameAs(expected);
    }

    @Test
    void get_unknownId_propagatesNotFound() {
        // Arrange
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Book with id 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 99 not found");
    }

    @Test
    void create_validDto_delegatesToServiceAndReturnsCreated() {
        // Arrange
        BookDto input = dto(null, "New");
        BookDto created = dto(10L, "New");
        when(service.create(input)).thenReturn(created);

        // Act
        BookDto result = controller.create(input);

        // Assert
        ArgumentCaptor<BookDto> captor = ArgumentCaptor.forClass(BookDto.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue()).isSameAs(input);
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void create_serviceFails_propagatesException() {
        // Arrange
        BookDto input = dto(null, "New");
        when(service.create(input)).thenThrow(new IllegalStateException("db down"));

        // Act + Assert
        assertThatThrownBy(() -> controller.create(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    @Test
    void update_existingId_passesIdAndDtoToService() {
        // Arrange
        BookDto input = dto(null, "Updated");
        BookDto updated = dto(3L, "Updated");
        when(service.update(3L, input)).thenReturn(updated);

        // Act
        BookDto result = controller.update(3L, input);

        // Assert
        verify(service).update(3L, input);
        assertThat(result.getTitle()).isEqualTo("Updated");
    }

    @Test
    void update_unknownId_propagatesNotFound() {
        // Arrange
        BookDto input = dto(null, "Updated");
        when(service.update(404L, input)).thenThrow(new ResourceNotFoundException("Book with id 404 not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.update(404L, input))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_existingId_delegatesToService() {
        // Arrange (service.delete — void, нічого стабити не треба)

        // Act
        controller.delete(5L);

        // Assert
        verify(service).delete(5L);
    }

    @Test
    void delete_serviceDeniesAccess_propagatesAccessDenied() {
        // Arrange
        doThrow(new AccessDeniedException("Access Denied")).when(service).delete(5L);

        // Act + Assert
        assertThatThrownBy(() -> controller.delete(5L))
                .isInstanceOf(AccessDeniedException.class);
    }
}

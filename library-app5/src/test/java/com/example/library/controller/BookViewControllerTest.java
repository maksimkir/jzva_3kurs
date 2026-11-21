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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookViewControllerTest {

    @Mock
    private BookService service;

    @InjectMocks
    private BookViewController controller;

    private static BookDto dto(Long id, String title) {
        BookDto d = new BookDto();
        d.setId(id);
        d.setTitle(title);
        d.setAuthor("Author");
        return d;
    }

    @Test
    void list_booksExist_addsBooksToModelAndReturnsListView() {
        // Arrange
        List<BookDto> books = List.of(dto(1L, "A"));
        when(service.findAll()).thenReturn(books);
        Model model = new ExtendedModelMap();

        // Act
        String view = controller.list(model);

        // Assert
        assertThat(view).isEqualTo("books/list");
        assertThat(model.asMap().get("books")).isEqualTo(books);
    }

    @Test
    void newForm_always_addsEmptyBookAndReturnsFormView() {
        // Arrange
        Model model = new ExtendedModelMap();

        // Act
        String view = controller.newForm(model);

        // Assert
        assertThat(view).isEqualTo("books/form");
        BookDto book = (BookDto) model.asMap().get("book");
        assertThat(book).isNotNull();
        assertThat(book.getId()).isNull();
        verifyNoInteractions(service);
    }

    @Test
    void editForm_existingId_addsLoadedBookAndReturnsFormView() {
        // Arrange
        BookDto loaded = dto(1L, "Loaded");
        when(service.findById(1L)).thenReturn(loaded);
        Model model = new ExtendedModelMap();

        // Act
        String view = controller.editForm(1L, model);

        // Assert
        assertThat(view).isEqualTo("books/form");
        assertThat(model.asMap().get("book")).isSameAs(loaded);
    }

    @Test
    void editForm_unknownId_propagatesNotFound() {
        // Arrange
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Book with id 99 not found"));
        Model model = new ExtendedModelMap();

        // Act + Assert
        assertThatThrownBy(() -> controller.editForm(99L, model))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void save_validNewBook_createsAndRedirects() {
        // Arrange
        BookDto book = dto(null, "New");
        BindingResult result = new BeanPropertyBindingResult(book, "book");

        // Act
        String view = controller.save(book, result);

        // Assert
        assertThat(view).isEqualTo("redirect:/books");
        ArgumentCaptor<BookDto> captor = ArgumentCaptor.forClass(BookDto.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue()).isSameAs(book);
        verify(service, never()).update(anyLong(), any(BookDto.class));
    }

    @Test
    void save_validExistingBook_updatesByIdAndRedirects() {
        // Arrange
        BookDto book = dto(7L, "Existing");
        BindingResult result = new BeanPropertyBindingResult(book, "book");

        // Act
        String view = controller.save(book, result);

        // Assert
        assertThat(view).isEqualTo("redirect:/books");
        verify(service).update(7L, book);
        verify(service, never()).create(any(BookDto.class));
    }

    @Test
    void save_bindingErrors_returnsFormViewAndDoesNotSave() {
        // Arrange
        BookDto book = dto(null, "");
        BindingResult result = new BeanPropertyBindingResult(book, "book");
        result.addError(new FieldError("book", "title", "must not be blank"));

        // Act
        String view = controller.save(book, result);

        // Assert
        assertThat(view).isEqualTo("books/form");
        verifyNoInteractions(service);
    }

    @Test
    void delete_existingId_deletesAndRedirects() {
        // Arrange (void-метод сервісу)

        // Act
        String view = controller.delete(3L);

        // Assert
        assertThat(view).isEqualTo("redirect:/books");
        verify(service).delete(3L);
    }

    @Test
    void delete_serviceDeniesAccess_propagatesAccessDenied() {
        // Arrange
        doThrow(new AccessDeniedException("Access Denied")).when(service).delete(3L);

        // Act + Assert
        assertThatThrownBy(() -> controller.delete(3L))
                .isInstanceOf(AccessDeniedException.class);
    }
}

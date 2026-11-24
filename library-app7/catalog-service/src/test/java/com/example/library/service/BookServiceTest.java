package com.example.library.service;

import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.notification.BookNotificationService;
import com.example.library.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести BookService: без Spring-контексту, залежності замоковані (@Mock/@InjectMocks).
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;

    @Mock
    private BookMapper mapper;

    @Mock
    private BookNotificationService notifications;

    @InjectMocks
    private BookService service;

    private static Book book(Long id, String title) {
        Book b = new Book();
        b.setId(id);
        b.setTitle(title);
        b.setAuthor("Some Author");
        return b;
    }

    private static BookDto dto(Long id, String title) {
        BookDto d = new BookDto();
        d.setId(id);
        d.setTitle(title);
        d.setAuthor("Some Author");
        return d;
    }

    // ---------------------------------------------------------------- findAll

    @Test
    void findAll_booksExist_returnsMappedDtos() {
        // Arrange
        List<Book> books = List.of(book(1L, "Clean Code"), book(2L, "Effective Java"));
        List<BookDto> dtos = List.of(dto(1L, "Clean Code"), dto(2L, "Effective Java"));
        when(repository.findAll()).thenReturn(books);
        when(mapper.toDtoList(books)).thenReturn(dtos);

        // Act
        List<BookDto> result = service.findAll();

        // Assert
        assertThat(result).containsExactlyElementsOf(dtos);
        verify(repository).findAll();
    }

    @Test
    void findAll_noBooks_returnsEmptyList() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of());
        when(mapper.toDtoList(List.of())).thenReturn(List.of());

        // Act
        List<BookDto> result = service.findAll();

        // Assert
        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------- findById

    @Test
    void findById_existingId_returnsMappedDto() {
        // Arrange
        Book entity = book(1L, "Clean Code");
        BookDto expected = dto(1L, "Clean Code");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(expected);

        // Act
        BookDto result = service.findById(1L);

        // Assert
        assertThat(result).isSameAs(expected);
        assertThat(result.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    void findById_unknownId_throwsResourceNotFoundException() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 99 not found");
        verifyNoInteractions(mapper);
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_validDto_savesMappedEntityAndReturnsSavedDto() {
        // Arrange
        BookDto input = dto(null, "DDD");
        Book entity = book(null, "DDD");
        Book saved = book(10L, "DDD");
        BookDto output = dto(10L, "DDD");
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(output);

        // Act
        BookDto result = service.create(input);

        // Assert
        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(entity);
        assertThat(captor.getValue().getTitle()).isEqualTo("DDD");
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void create_repositoryFails_propagatesExceptionAndDoesNotMapResult() {
        // Arrange
        BookDto input = dto(null, "DDD");
        Book entity = book(null, "DDD");
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenThrow(new DataIntegrityViolationException("constraint violation"));

        // Act + Assert
        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(mapper, never()).toDto(any(Book.class));
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_existingId_appliesDtoToEntityThenSaves() {
        // Arrange
        Book existing = book(1L, "Old title");
        BookDto input = dto(null, "New title");
        BookDto output = dto(1L, "New title");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(output);

        // Act
        BookDto result = service.update(1L, input);

        // Assert
        InOrder order = inOrder(mapper, repository);
        order.verify(mapper).updateEntity(input, existing);
        order.verify(repository).save(existing);
        assertThat(result.getTitle()).isEqualTo("New title");
    }

    @Test
    void update_unknownId_throwsAndNeverSaves() {
        // Arrange
        when(repository.findById(42L)).thenReturn(Optional.empty());
        BookDto input = dto(null, "Whatever");

        // Act + Assert
        assertThatThrownBy(() -> service.update(42L, input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 42 not found");
        verify(repository, never()).save(any(Book.class));
        verify(mapper, never()).updateEntity(any(BookDto.class), any(Book.class));
    }

    // ---------------------------------------------------------------- delete

    @Test
    void delete_existingId_deletesFoundEntity() {
        // Arrange
        Book existing = book(1L, "To delete");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        service.delete(1L);

        // Assert
        verify(repository).delete(existing);
    }

    @Test
    void delete_unknownId_throwsAndNeverDeletes() {
        // Arrange
        when(repository.findById(7L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 7 not found");
        verify(repository, never()).delete(any(Book.class));
    }

    @Test
    void delete_methodSecurityAnnotation_requiresAdminRole() throws Exception {
        // Arrange
        Method method = BookService.class.getMethod("delete", Long.class);

        // Act
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    // ---------------------------------------------------------------- count

    @Test
    void count_booksExist_returnsRepositoryCount() {
        // Arrange
        when(repository.count()).thenReturn(5L);

        // Act
        long result = service.count();

        // Assert
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void count_emptyRepository_returnsZero() {
        // Arrange
        when(repository.count()).thenReturn(0L);

        // Act
        long result = service.count();

        // Assert
        assertThat(result).isZero();
    }

    // ---------- ЛР5: fire-and-forget сповіщення ----------
    @Test
    void create_validDto_submitsNewBookNotificationWithSavedBook() {
        // Arrange
        BookDto input = dto(null, "Clean Code");
        Book entity = book(null, "Clean Code");
        Book savedEntity = book(10L, "Clean Code");
        BookDto saved = dto(10L, "Clean Code");
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(saved);

        // Act
        BookDto result = service.create(input);

        // Assert
        assertThat(result).isSameAs(saved);
        verify(notifications).notifyNewBook(saved);
    }

    @Test
    void create_notificationExecutorFull_bookStillCreatedWithoutException() {
        // Arrange
        BookDto input = dto(null, "Clean Code");
        Book entity = book(null, "Clean Code");
        Book savedEntity = book(11L, "Clean Code");
        BookDto saved = dto(11L, "Clean Code");
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(saved);
        org.mockito.Mockito.doThrow(new TaskRejectedException("pool is full")).when(notifications).notifyNewBook(saved);

        // Act
        BookDto result = service.create(input);

        // Assert
        assertThat(result).isSameAs(saved);
        verify(notifications).notifyNewBook(saved);
    }

    @Test
    void create_repositoryFails_noNotificationSent() {
        // Arrange
        BookDto input = dto(null, "Clean Code");
        Book entity = book(null, "Clean Code");
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Act + Assert
        assertThatThrownBy(() -> service.create(input)).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(notifications);
    }
}

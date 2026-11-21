package com.example.library.service;

import com.example.library.aop.NormalizeText;
import com.example.library.aop.RetryOnTransientFailure;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookService {

    private final BookRepository repository;
    private final BookMapper mapper;

    @Transactional(readOnly = true)
    public List<BookDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public BookDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    /** ЛР4: @NormalizeText чистить текстові поля, @RetryOnTransientFailure повторює при deadlock/lock timeout. */
    @NormalizeText
    @RetryOnTransientFailure
    public BookDto create(BookDto dto) {
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @NormalizeText
    @RetryOnTransientFailure
    public BookDto update(Long id, BookDto dto) {
        Book book = getEntity(id);
        mapper.updateEntity(dto, book);
        return mapper.toDto(repository.save(book));
    }

    /**
     * ЛР4 — НАВМИСНА демонстрація проблеми self-invocation (не баг, який треба виправляти).
     * <p>
     * Виклик {@code create(dto)} тут — це {@code this.create(dto)}: він іде напряму в об'єкт BookService,
     * ОМИНАЮЧИ Spring-проксі. Тому аспекти на {@link #create(BookDto)} (@NormalizeText, @RetryOnTransientFailure)
     * НЕ спрацьовують — без жодної помилки чи попередження: пробіли зберігаються як є, повтору при збої немає.
     * Порівняйте з {@code bookService.create(dto)}, викликаним з іншого біна (контролера) — там усе працює.
     * Демонструється ендпоінтами {@code POST /api/demo/aop/books/external} та {@code .../self-invocation}.
     */
    public BookDto createViaSelfInvocation(BookDto dto) {
        return create(dto); // == this.create(dto) -> проксі не задіяний, advice не виконується
    }

    /**
     * Acceptance criteria 7: захист на рівні методу.
     * Filter chain пропускає DELETE для будь-якого автентифікованого користувача,
     * але цей метод виконається лише для ролі ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    private Book getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + id + " not found"));
    }
}

package com.example.library.service;

import com.example.library.aop.NormalizeText;
import com.example.library.aop.RetryOnTransientFailure;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.notification.BookNotificationService;
import com.example.library.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookService {

    private final BookRepository repository;
    private final BookMapper mapper;
    private final BookNotificationService notifications;

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
        BookDto saved = mapper.toDto(repository.save(mapper.toEntity(dto)));
        notifyAfterCommit(saved);
        return saved;
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

    /**
     * ЛР5 — fire-and-forget: лист про нову книгу відправляється у фоні і НЕ блокує create().
     * Ставимо його в чергу лише ПІСЛЯ коміту транзакції — щоб не розсилати листи про книгу, яка відкотилась.
     */
    private void notifyAfterCommit(BookDto saved) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitNotification(saved);
                }
            });
        } else {
            submitNotification(saved);
        }
    }

    /**
     * Явне рішення для переповненого пулу: книгу вже збережено, тож сповіщення — best effort.
     * TaskRejectedException не повинен перетворити успішне створення книги на 500 — пишемо WARN і йдемо далі.
     */
    private void submitNotification(BookDto saved) {
        try {
            notifications.notifyNewBook(saved);
        } catch (TaskRejectedException e) {
            log.warn("[notify] executor is full, 'new book' email for id={} skipped: {}", saved.getId(), e.getMessage());
        }
    }

    private Book getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + id + " not found"));
    }
}

package com.example.library.aop;

import com.example.library.controller.AdminController;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.mapper.BookMapperImpl;
import com.example.library.notification.BookNotificationService;
import com.example.library.repository.BookRepository;
import com.example.library.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Перевірка, що анотації дійсно пов'язані з аспектами через Spring AOP-проксі,
 * і НАВМИСНА демонстрація проблеми self-invocation.
 * <p>
 * Мінімальний Spring-контекст (лише аспекти + BookService + AdminController), без БД, Keycloak і @SpringBootTest.
 * Репозиторій — Mockito-mock, тож видно, ЩО саме дійшло до збереження.
 */
@SpringJUnitConfig(AopProxyAndSelfInvocationTest.Config.class)
class AopProxyAndSelfInvocationTest {

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        BookRepository bookRepository() {
            return Mockito.mock(BookRepository.class);
        }

        @Bean
        BookService bookService(BookRepository repository) {
            return new BookService(repository, new BookMapperImpl(), Mockito.mock(BookNotificationService.class));
        }

        @Bean
        AdminController adminController(BookService bookService) {
            return new AdminController(bookService);
        }

        @Bean
        NormalizeTextAspect normalizeTextAspect() {
            return new NormalizeTextAspect();
        }

        @Bean
        RetryOnTransientFailureAspect retryAspect() {
            return new RetryOnTransientFailureAspect(ms -> { }); // без реальних пауз у тесті
        }

        @Bean
        RateLimitedAspect rateLimitedAspect() {
            return new RateLimitedAspect();
        }
    }

    @Autowired
    private BookService bookService;

    @Autowired
    private AdminController adminController;

    @Autowired
    private BookRepository repository;

    @BeforeEach
    void resetRepository() {
        Mockito.reset(repository);
        when(repository.save(any(Book.class))).thenAnswer(inv -> {
            Book saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
    }

    private static BookDto messyBook() {
        BookDto dto = new BookDto();
        dto.setTitle("   Clean    Code ");
        dto.setAuthor(" Robert  C. Martin");
        return dto;
    }

    private Book savedEntity() {
        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(repository, Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ---------- Сценарій 1: анотація правильно пов'язана з аспектом ----------
    @Test
    void annotations_haveRuntimeRetentionAndMethodTarget() {
        for (Class<?> annotation : new Class<?>[]{RetryOnTransientFailure.class, NormalizeText.class, RateLimited.class}) {
            assertThat(annotation.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(annotation.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        }
    }

    @Test
    void beansWithAnnotatedMethods_areWrappedInAopProxy() {
        assertThat(AopUtils.isAopProxy(bookService)).isTrue();
        assertThat(AopUtils.isAopProxy(adminController)).isTrue();
    }

    // ---------- Сценарії 2–3: зовнішній виклик через проксі -> advice спрацьовує ----------
    @Test
    void create_calledFromOutside_normalizeAdviceCleansTextBeforeSave() {
        // Act
        BookDto result = bookService.create(messyBook());

        // Assert
        assertThat(savedEntity().getTitle()).isEqualTo("Clean Code");
        assertThat(savedEntity().getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(result.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    void create_calledFromOutside_transientFailureIsRetried() {
        // Arrange
        when(repository.save(any(Book.class)))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        BookDto result = bookService.create(messyBook());

        // Assert
        assertThat(result.getTitle()).isEqualTo("Clean Code");
        verify(repository, times(2)).save(any(Book.class));
    }

    @Test
    void stats_calledFromOutside_rateLimitAdviceBlocksSixthCall() {
        // Arrange
        when(repository.count()).thenReturn(3L);
        for (int i = 0; i < 5; i++) {
            adminController.stats();
        }

        // Act + Assert
        assertThatThrownBy(() -> adminController.stats()).isInstanceOf(RateLimitExceededException.class);
        verify(repository, times(5)).count(); // 6-й виклик не дійшов до методу
    }

    // ---------- Сценарій 4: self-invocation -> advice НЕ спрацьовує (свідомо, без помилки) ----------
    @Test
    void create_calledViaSelfInvocation_normalizeAdviceSilentlySkipped() {
        // Act: той самий create(...), але викликаний як this.create(...) зсередини BookService
        BookDto result = bookService.createViaSelfInvocation(messyBook());

        // Assert: жодного винятку, але дані НЕ нормалізовані — проксі оминуто
        assertThat(savedEntity().getTitle()).isEqualTo("   Clean    Code ");
        assertThat(savedEntity().getAuthor()).isEqualTo(" Robert  C. Martin");
        assertThat(result.getTitle()).isEqualTo("   Clean    Code ");
    }

    @Test
    void create_calledViaSelfInvocation_transientFailureIsNotRetried() {
        // Arrange
        CannotAcquireLockException deadlock = new CannotAcquireLockException("deadlock");
        when(repository.save(any(Book.class))).thenThrow(deadlock).thenAnswer(inv -> inv.getArgument(0));

        // Act + Assert: при зовнішньому виклику була б друга спроба і успіх, тут — одразу помилка
        assertThatThrownBy(() -> bookService.createViaSelfInvocation(messyBook())).isSameAs(deadlock);
        verify(repository, times(1)).save(any(Book.class));
    }
}

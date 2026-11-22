package com.example.notification.api;

import com.example.library.common.notification.BookCreatedNotification;
import com.example.notification.config.NotificationProperties;
import com.example.notification.mail.MailGateway;
import com.example.notification.store.PendingAnnouncement;
import com.example.notification.store.PendingAnnouncementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCreatedServiceTest {

    @Mock private PendingAnnouncementRepository repository;
    @Mock private MailGateway mail;

    private BookCreatedService service() {
        return new BookCreatedService(repository, mail, new NotificationProperties("key", "readers@library.local"),
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static final BookCreatedNotification BOOK = new BookCreatedNotification(7L, "Release It!", "Michael Nygard");

    @Test
    void onBookCreated_newBook_queuedForDigestAndEmailSent() {
        when(repository.existsByBookId(7L)).thenReturn(false);

        BookCreatedService.Result result = service().onBookCreated(BOOK);

        assertThat(result).isEqualTo(BookCreatedService.Result.ACCEPTED);
        ArgumentCaptor<PendingAnnouncement> saved = ArgumentCaptor.forClass(PendingAnnouncement.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getBookId()).isEqualTo(7L);
        assertThat(saved.getValue().getAnnouncedAt()).isNull();
        assertThat(saved.getValue().getReceivedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        verify(mail).send(eq("readers@library.local"), contains("Release It!"), anyString());
    }

    @Test
    void onBookCreated_duplicate_isIgnoredWithoutEmail() {
        when(repository.existsByBookId(7L)).thenReturn(true);

        assertThat(service().onBookCreated(BOOK)).isEqualTo(BookCreatedService.Result.DUPLICATE);
        verify(repository, never()).save(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void onBookCreated_concurrentDuplicate_uniqueConstraintMeansDuplicate() {
        when(repository.existsByBookId(7L)).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("uq_pending_announcements_book"));

        assertThat(service().onBookCreated(BOOK)).isEqualTo(BookCreatedService.Result.DUPLICATE);
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void onBookCreated_mailFails_bookStillQueuedAndAccepted() {
        when(repository.existsByBookId(7L)).thenReturn(false);
        doThrow(new IllegalStateException("SMTP down")).when(mail).send(anyString(), anyString(), anyString());

        assertThat(service().onBookCreated(BOOK)).isEqualTo(BookCreatedService.Result.ACCEPTED);
        verify(repository).save(any());
    }
}

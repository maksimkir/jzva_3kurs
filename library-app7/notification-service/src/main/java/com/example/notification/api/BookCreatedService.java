package com.example.notification.api;

import com.example.library.common.notification.BookCreatedNotification;
import com.example.notification.config.NotificationProperties;
import com.example.notification.mail.MailGateway;
import com.example.notification.store.PendingAnnouncement;
import com.example.notification.store.PendingAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * ЛР7 — обробка повідомлення «створено книгу» від catalog-service:
 * <ol>
 *   <li>книга ставиться в чергу дайджесту (ідемпотентно: той самий bookId двічі — один запис);</li>
 *   <li>підписникам іде лист «Нова книга». Збій SMTP не губить книгу: вона вже в черзі і потрапить у дайджест.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookCreatedService {

    public enum Result { ACCEPTED, DUPLICATE }

    private final PendingAnnouncementRepository repository;
    private final MailGateway mailGateway;
    private final NotificationProperties properties;
    private final Clock clock;

    public Result onBookCreated(BookCreatedNotification notification) {
        if (repository.existsByBookId(notification.bookId())) {
            log.info("[notify] book {} already received — ignoring duplicate", notification.bookId());
            return Result.DUPLICATE;
        }
        PendingAnnouncement pending = new PendingAnnouncement();
        pending.setBookId(notification.bookId());
        pending.setTitle(notification.title());
        pending.setAuthor(notification.author());
        pending.setReceivedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        try {
            repository.save(pending);
        } catch (DataIntegrityViolationException race) { // два однакові запити одночасно — спрацював UNIQUE
            return Result.DUPLICATE;
        }

        try {
            mailGateway.send(properties.subscribers(),
                    "Нова книга в бібліотеці: " + notification.title(),
                    "«" + notification.title() + "» — " + notification.author() + ". Чекаємо на вас!");
        } catch (RuntimeException e) {
            log.error("[notify] 'new book' email for book {} failed, it will still be in the digest: {}",
                    notification.bookId(), e.toString());
        }
        return Result.ACCEPTED;
    }
}

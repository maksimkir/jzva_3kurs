package com.example.notification.digest;

import com.example.notification.mail.MailGateway;
import com.example.notification.store.PendingAnnouncement;
import com.example.notification.store.PendingAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ЛР6 (у ЛР7 переїхав у notification-service) — бізнес-логіка дайджесту новинок, викликається з NewBooksDigestJob.
 * <ol>
 *   <li>бере до batchSize книг з черги, які ще не анонсувались (announced_at IS NULL);</li>
 *   <li>надсилає ОДИН лист-дайджест;</li>
 *   <li>позначає їх як анонсовані — у тій самій транзакції.</li>
 * </ol>
 * Ідемпотентність: другий запуск одразу після першого нічого не знаходить і нічого не надсилає.
 * Лист не пішов — виняток, транзакція відкочується, позначки не ставляться, наступна спроба надішле те саме.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewBooksDigestService {

    private final PendingAnnouncementRepository repository;
    private final MailGateway mailGateway;
    private final Clock clock;

    /** @return скільки книг увійшло в дайджест (0 — нових немає, лист не надсилався). */
    @Transactional
    public int sendDigest(int batchSize, String recipients) {
        List<PendingAnnouncement> fresh = repository.findByAnnouncedAtIsNullOrderByIdAsc(PageRequest.of(0, batchSize));
        if (fresh.isEmpty()) {
            log.info("[digest] no new books since the last digest — nothing to send");
            return 0;
        }
        String body = fresh.stream()
                .map(b -> "• «" + b.getTitle() + "» — " + b.getAuthor())
                .collect(Collectors.joining("\n"));
        mailGateway.send(recipients, "Новинки бібліотеки (" + fresh.size() + ")", body);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        fresh.forEach(item -> item.setAnnouncedAt(now)); // dirty checking -> UPDATE при коміті
        log.info("[digest] sent digest with {} book(s) to {}", fresh.size(), recipients);
        return fresh.size();
    }
}

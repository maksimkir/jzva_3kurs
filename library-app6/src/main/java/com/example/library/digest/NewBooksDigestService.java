package com.example.library.digest;

import com.example.library.entity.Book;
import com.example.library.notification.MailGateway;
import com.example.library.repository.BookRepository;
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
 * ЛР6 — бізнес-логіка дайджесту новинок (викликається з NewBooksDigestJob).
 * <ol>
 *   <li>бере до batchSize книг, які ще не анонсувались (announced_at IS NULL);</li>
 *   <li>надсилає ОДИН лист-дайджест;</li>
 *   <li>позначає книги як анонсовані — у тій самій транзакції.</li>
 * </ol>
 * Ідемпотентність: другий запуск одразу після першого не знаходить нових книг і нічого не надсилає.
 * Якщо лист не відправився — виняток, транзакція відкочується, позначки не ставляться, і наступна спроба
 * надішле ті самі книги (нічого не губиться і не дублюється).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewBooksDigestService {

    private final BookRepository books;
    private final MailGateway mailGateway;
    private final Clock clock;

    /** @return скільки книг увійшло в дайджест (0 — нових немає, лист не надсилався). */
    @Transactional
    public int sendDigest(int batchSize, String recipients) {
        List<Book> fresh = books.findByAnnouncedAtIsNullOrderByIdAsc(PageRequest.of(0, batchSize));
        if (fresh.isEmpty()) {
            log.info("[digest] no new books since the last digest — nothing to send");
            return 0;
        }
        String body = fresh.stream()
                .map(b -> "• «" + b.getTitle() + "» — " + b.getAuthor())
                .collect(Collectors.joining("\n"));
        mailGateway.send(recipients, "Новинки бібліотеки (" + fresh.size() + ")", body);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        fresh.forEach(book -> book.setAnnouncedAt(now)); // dirty checking -> UPDATE при коміті
        log.info("[digest] sent digest with {} book(s) to {}", fresh.size(), recipients);
        return fresh.size();
    }
}

package com.example.notification.store;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * ЛР7 — книга, про яку notification-service дізнався від catalog-service і яку ще треба включити в дайджест.
 * notification-service НЕ читає таблицю books іншого сервісу — у нього власні дані (власна БД).
 */
@Entity
@Table(name = "pending_announcements")
@Getter
@Setter
@NoArgsConstructor
public class PendingAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** id книги в catalog-service; UNIQUE — повторне повідомлення не створить дубль. */
    @Column(name = "book_id", nullable = false, unique = true)
    private Long bookId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Коли книгу включено в дайджест (null — ще ні). */
    @Column(name = "announced_at")
    private Instant announcedAt;
}

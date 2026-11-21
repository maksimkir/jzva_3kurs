package com.example.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(length = 20)
    private String isbn;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(length = 2000)
    private String description;

    /** ЛР6: коли книгу включено в дайджест новинок (null — ще не анонсована). */
    @Column(name = "announced_at")
    private Instant announcedAt;
}

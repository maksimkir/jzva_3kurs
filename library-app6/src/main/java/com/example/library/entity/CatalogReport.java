package com.example.library.entity;

import com.example.library.report.ReportPeriod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/** ЛР6: знімок статистики каталогу за період (результат планового завдання). */
@Entity
@Table(name = "catalog_reports",
        uniqueConstraints = @UniqueConstraint(name = "uq_catalog_reports_period", columnNames = {"period", "period_start"}))
@Getter
@Setter
@NoArgsConstructor
public class CatalogReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // звичайний VARCHAR (як у Liquibase), а не нативний enum БД
    @Column(nullable = false, length = 10)
    private ReportPeriod period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "total_books", nullable = false)
    private long totalBooks;

    @Column(name = "books_with_isbn", nullable = false)
    private long booksWithIsbn;

    @Column(name = "top_authors", length = 2000)
    private String topAuthors;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}

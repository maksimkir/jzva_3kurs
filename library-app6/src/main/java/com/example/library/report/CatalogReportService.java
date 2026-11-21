package com.example.library.report;

import com.example.library.entity.CatalogReport;
import com.example.library.repository.BookRepository;
import com.example.library.repository.CatalogReportRepository;
import com.example.library.scheduling.SchedulingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ЛР6 — бізнес-логіка планового звіту по каталогу (викликається з CatalogReportJob).
 * <p>
 * Ідемпотентність: рядок звіту однозначно визначається парою (period, periodStart).
 * Повторний запуск за той самий період НЕ створює дубль і, якщо каталог не змінився, нічого не змінює
 * (навіть generatedAt). Межа транзакції — цей метод.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogReportService {

    private final BookRepository books;
    private final CatalogReportRepository reports;
    private final SchedulingProperties properties;
    private final Clock clock;

    @Transactional
    public CatalogReport generate(ReportPeriod period, int topAuthors) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zoneId()));
        LocalDate periodStart = period.startOf(today);

        long total = books.count();
        long withIsbn = books.countByIsbnIsNotNull();
        String top = books.findTopAuthors(PageRequest.of(0, topAuthors)).stream()
                .map(row -> row[0] + " (" + row[1] + ")")
                .collect(Collectors.joining("; "));

        CatalogReport report = reports.findByPeriodAndPeriodStart(period, periodStart).orElse(null);
        if (report != null && report.getTotalBooks() == total && report.getBooksWithIsbn() == withIsbn
                && Objects.equals(report.getTopAuthors(), top)) {
            log.info("[report] {} report for {} is up to date — nothing changed", period, periodStart);
            return report;
        }
        boolean created = report == null;
        if (created) {
            report = new CatalogReport();
            report.setPeriod(period);
            report.setPeriodStart(periodStart);
        }
        report.setTotalBooks(total);
        report.setBooksWithIsbn(withIsbn);
        report.setTopAuthors(top);
        report.setGeneratedAt(clock.instant().truncatedTo(ChronoUnit.MICROS)); // точність БД — мікросекунди
        CatalogReport saved = reports.save(report);
        log.info("[report] {} report for {} {}: total={}, withIsbn={}, top=[{}]",
                period, periodStart, created ? "created" : "updated", total, withIsbn, top);
        return saved;
    }
}

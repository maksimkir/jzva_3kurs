package com.example.library.repository;

import com.example.library.entity.CatalogReport;
import com.example.library.report.ReportPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CatalogReportRepository extends JpaRepository<CatalogReport, Long> {

    Optional<CatalogReport> findByPeriodAndPeriodStart(ReportPeriod period, LocalDate periodStart);

    List<CatalogReport> findTop20ByOrderByPeriodStartDescIdDesc();
}

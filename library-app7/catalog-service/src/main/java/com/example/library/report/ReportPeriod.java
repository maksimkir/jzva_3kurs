package com.example.library.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Період звіту по каталогу; визначає дату початку періоду, до якої «прив'язується» рядок звіту. */
public enum ReportPeriod {
    DAILY {
        @Override
        public LocalDate startOf(LocalDate day) {
            return day;
        }
    },
    WEEKLY {
        @Override
        public LocalDate startOf(LocalDate day) {
            return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    };

    public abstract LocalDate startOf(LocalDate day);
}

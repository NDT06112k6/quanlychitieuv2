package com.example.expensetracker.budget;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPeriodTests {

    @ParameterizedTest
    @CsvSource({
            "2021-01-01, 2020-W53, 2020-12-28, 2021-01-03",
            "2024-12-30, 2025-W01, 2024-12-30, 2025-01-05",
            "2026-11-01, 2026-W44, 2026-10-26, 2026-11-01"
    })
    void handlesIsoWeeksAcrossCalendarBoundaries(String date, String expectedPeriod, String start, String end) {
        BudgetPeriod period = BudgetPeriod.containing(BudgetType.WEEKLY, LocalDate.parse(date));

        assertThat(period.period()).isEqualTo(expectedPeriod);
        assertThat(period.startDate()).isEqualTo(LocalDate.parse(start));
        assertThat(period.endDate()).isEqualTo(LocalDate.parse(end));
    }

    @Test
    void includesLeapDayInMonthlyPeriod() {
        BudgetPeriod period = new BudgetPeriod(BudgetType.MONTHLY, "2024-02");

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(period.endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @ParameterizedTest
    @CsvSource({"WEEKLY,2021-W53", "WEEKLY,2026-W00", "MONTHLY,2026-13", "MONTHLY,2026-1"})
    void rejectsInvalidPeriods(BudgetType type, String value) {
        assertThatThrownBy(() -> new BudgetPeriod(type, value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampsBoundaryWeekToMySqlDateRange() {
        BudgetPeriod period = BudgetPeriod.containing(BudgetType.WEEKLY, LocalDate.of(9999, 12, 31));

        assertThat(period.dateRange().endDate()).isEqualTo(LocalDate.of(9999, 12, 31));
    }
}

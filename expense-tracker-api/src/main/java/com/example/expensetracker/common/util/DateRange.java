package com.example.expensetracker.common.util;

import java.time.LocalDate;
import java.util.Objects;

public record DateRange(LocalDate startDate, LocalDate endDate) {

    public static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    public static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    public DateRange {
        Objects.requireNonNull(startDate, "Start date is required");
        Objects.requireNonNull(endDate, "End date is required");
        if (startDate.isAfter(endDate) || startDate.isBefore(MINIMUM_DATE)
                || endDate.isAfter(MAXIMUM_DATE)) {
            throw new IllegalArgumentException("Invalid MySQL date range");
        }
    }

    public static DateRange allDates() {
        DateRange range = new DateRange(MINIMUM_DATE, MAXIMUM_DATE);
        return range;
    }
}

package com.example.expensetracker.common.web;

import com.example.expensetracker.common.util.DateRange;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record DateRangeParameters(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    public DateRange toRange() {
        LocalDate start = startDate;
        LocalDate end = endDate;
        if (start == null) {
            start = DateRange.MINIMUM_DATE;
        }
        if (end == null) {
            end = DateRange.MAXIMUM_DATE;
        }
        DateRange range = new DateRange(start, end);
        return range;
    }
}

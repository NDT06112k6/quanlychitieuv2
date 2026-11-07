package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.web.DateRangeParameters;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record TransactionQueryRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @Positive Long categoryId, CategoryType type) {

    public TransactionFilter toFilter() {
        DateRangeParameters range = new DateRangeParameters(startDate, endDate);
        TransactionFilter filter = new TransactionFilter(categoryId, type, range.toRange());
        return filter;
    }
}

package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.common.util.DomainValidation;
import java.util.Objects;

public record TransactionFilter(Long categoryId, CategoryType type, DateRange dateRange) {

    public TransactionFilter {
        Objects.requireNonNull(dateRange, "Date range is required");
        if (categoryId != null) {
            DomainValidation.requirePositiveId(categoryId);
        }
    }

    public static TransactionFilter all() {
        TransactionFilter filter = new TransactionFilter(null, null, DateRange.allDates());
        return filter;
    }
}

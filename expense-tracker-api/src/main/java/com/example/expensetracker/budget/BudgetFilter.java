package com.example.expensetracker.budget;

import com.example.expensetracker.common.util.DomainValidation;

public record BudgetFilter(Long categoryId, BudgetType type, String period) {

    public BudgetFilter {
        if (categoryId != null) {
            DomainValidation.requirePositiveId(categoryId);
        }
        if (period != null) {
            new BudgetPeriod(type, period);
        }
    }

    public static BudgetFilter forPeriod(BudgetPeriod period) {
        BudgetFilter filter = new BudgetFilter(null, period.type(), period.period());
        return filter;
    }
}

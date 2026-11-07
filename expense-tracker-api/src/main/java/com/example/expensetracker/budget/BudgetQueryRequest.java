package com.example.expensetracker.budget;

import jakarta.validation.constraints.Positive;

public record BudgetQueryRequest(@Positive Long categoryId, BudgetType type, String period) {

    public BudgetFilter toFilter() {
        BudgetFilter filter = new BudgetFilter(categoryId, type, period);
        return filter;
    }
}

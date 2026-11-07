package com.example.expensetracker.budget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BudgetAlertQueryRequest(@NotNull BudgetType type, @NotBlank String period) {

    public BudgetPeriod toPeriod() {
        BudgetPeriod budgetPeriod = new BudgetPeriod(type, period);
        return budgetPeriod;
    }
}

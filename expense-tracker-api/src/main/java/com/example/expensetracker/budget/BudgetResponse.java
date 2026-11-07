package com.example.expensetracker.budget;

import java.math.BigDecimal;

public record BudgetResponse(Long id, Long categoryId, String categoryName,
        BudgetPeriod period, BigDecimal limitAmount) {

    public static BudgetResponse from(Budget budget) {
        BudgetPeriod period = new BudgetPeriod(budget.getType(), budget.getPeriod());
        BudgetResponse response = new BudgetResponse(budget.getId(), budget.getCategory().getId(),
                budget.getCategory().getName(), period, budget.getLimitAmount());
        return response;
    }
}

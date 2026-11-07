package com.example.expensetracker.budget;

import com.example.expensetracker.common.util.DomainValidation;
import java.math.BigDecimal;

public record BudgetAlert(BudgetResponse budget, BigDecimal spent, BigDecimal remaining,
        BigDecimal exceededAmount, BudgetStatus status) {

    public static BudgetAlert from(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getLimitAmount().subtract(spent);
        BigDecimal exceededAmount = remaining.negate().max(DomainValidation.ZERO_AMOUNT);
        BudgetStatus status = BudgetStatus.WITHIN_LIMIT;
        int comparison = spent.compareTo(budget.getLimitAmount());
        if (comparison > 0) {
            status = BudgetStatus.EXCEEDED;
        } else if (comparison == 0) {
            status = BudgetStatus.LIMIT_REACHED;
        }
        BudgetAlert alert = new BudgetAlert(BudgetResponse.from(budget), spent, remaining, exceededAmount, status);
        return alert;
    }
}

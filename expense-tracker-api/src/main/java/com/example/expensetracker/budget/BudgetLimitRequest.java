package com.example.expensetracker.budget;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BudgetLimitRequest(
        @NotNull @Positive @Digits(integer = DomainValidation.MONEY_INTEGER_DIGITS,
                fraction = DomainValidation.MONEY_SCALE) BigDecimal limitAmount) {

    public BudgetLimitRequest {
        limitAmount = DomainValidation.requireMoney(limitAmount);
    }
}

package com.example.expensetracker.budget;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Objects;

public record BudgetRequest(
        @NotNull @Positive Long categoryId,
        @Valid @NotNull BudgetPeriod period,
        @NotNull @Positive @Digits(integer = DomainValidation.MONEY_INTEGER_DIGITS,
                fraction = DomainValidation.MONEY_SCALE) BigDecimal limitAmount) {

    public BudgetRequest {
        DomainValidation.requirePositiveId(categoryId);
        Objects.requireNonNull(period, "Budget period is required");
        limitAmount = DomainValidation.requireMoney(limitAmount);
    }
}

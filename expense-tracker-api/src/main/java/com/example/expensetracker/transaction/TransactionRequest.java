package com.example.expensetracker.transaction;

import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record TransactionRequest(
        @NotNull @Positive Long categoryId,
        @NotNull @Positive @Digits(integer = DomainValidation.MONEY_INTEGER_DIGITS,
                fraction = DomainValidation.MONEY_SCALE) BigDecimal amount,
        @NotNull LocalDate occurredOn,
        @Size(max = TransactionRequest.MAXIMUM_NOTE_LENGTH) String note) {

    public static final int MAXIMUM_NOTE_LENGTH = 1000;

    public TransactionRequest {
        DomainValidation.requirePositiveId(categoryId);
        amount = DomainValidation.requireMoney(amount);
        Objects.requireNonNull(occurredOn, "Transaction date is required");
        new DateRange(occurredOn, occurredOn);
        if (note != null && note.length() > MAXIMUM_NOTE_LENGTH) {
            throw new IllegalArgumentException("Note is too long");
        }
    }
}

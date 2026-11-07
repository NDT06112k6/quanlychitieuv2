package com.example.expensetracker.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class DomainValidation {

    public static final int MONEY_SCALE = 2;
    public static final int MONEY_INTEGER_DIGITS = 13;
    public static final int MONEY_PRECISION = 15;
    public static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private DomainValidation() {
    }

    public static String requireText(String value, int maximumLength) {
        Objects.requireNonNull(value, "Text is required");
        String text = value.strip();
        if (text.isEmpty() || text.length() > maximumLength) {
            throw new IllegalArgumentException("Text length is invalid");
        }
        return text;
    }

    public static BigDecimal requireMoney(BigDecimal value) {
        Objects.requireNonNull(value, "Amount is required");
        if (value.signum() <= 0 || value.precision() - value.scale() > MONEY_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Amount must fit positive DECIMAL(15,2)");
        }
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new IllegalArgumentException("Amount must not require rounding");
        }
        BigDecimal amount = value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        return amount;
    }

    public static void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("A positive ID is required");
        }
    }
}

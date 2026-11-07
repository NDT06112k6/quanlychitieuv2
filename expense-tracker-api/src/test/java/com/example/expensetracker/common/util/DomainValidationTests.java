package com.example.expensetracker.common.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValidationTests {

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10000000000000.00", "0.001"})
    void rejectsAmountsThatCannotBeStoredExactly(String value) {
        assertThatThrownBy(() -> DomainValidation.requireMoney(new BigDecimal(value)))
                .isInstanceOfAny(IllegalArgumentException.class, ArithmeticException.class);
    }

    @Test
    void preservesLargestValidAmountWithoutFloatingPointRounding() {
        BigDecimal amount = DomainValidation.requireMoney(new BigDecimal("9999999999999.99"));

        assertThat(amount).isEqualByComparingTo("9999999999999.99");
    }

    @Test
    void rejectsReversedDateRange() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> new DateRange(start, end)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnboundedPagination() {
        assertThatThrownBy(() -> PageableUtil.bounded(Pageable.unpaged())).isInstanceOf(IllegalArgumentException.class);
    }
}

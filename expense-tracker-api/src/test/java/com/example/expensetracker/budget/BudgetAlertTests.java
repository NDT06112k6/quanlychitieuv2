package com.example.expensetracker.budget;

import com.example.expensetracker.support.DomainFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetAlertTests {

    @ParameterizedTest
    @CsvSource({
            "0.00, WITHIN_LIMIT, 12.34, 0.00",
            "12.33, WITHIN_LIMIT, 0.01, 0.00",
            "12.34, LIMIT_REACHED, 0.00, 0.00",
            "12.35, EXCEEDED, -0.01, 0.01"
    })
    void distinguishesAtLimitAndOverLimit(String spent, BudgetStatus status, String remaining, String exceeded) {
        BudgetPeriod period = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
        Budget budget = DomainFixtures.budget(period);

        BudgetAlert alert = BudgetAlert.from(budget, new BigDecimal(spent));

        assertThat(alert.status()).isEqualTo(status);
        assertThat(alert.remaining()).isEqualByComparingTo(remaining);
        assertThat(alert.exceededAmount()).isEqualByComparingTo(exceeded);
        assertThat(alert.exceededAmount().scale()).isEqualTo(2);
    }
}

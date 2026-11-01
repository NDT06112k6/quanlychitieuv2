package com.example.expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaMigrationTests {

    private static final long OWNER_ID = 101L;
    private static final long OTHER_USER_ID = 102L;
    private static final long CATEGORY_ID = 201L;
    private static final String MONTHLY_PERIOD = "2026-11";
    private static final String MONTHLY_TYPE = "MONTHLY";
    private static final String WEEKLY_PERIOD = "2026-W44";
    private static final String WEEKLY_TYPE = "WEEKLY";
    private static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("9999999999999.99");
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 11, 1);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    SchemaMigrationTests(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void createOwnersAndCategory() {
        String passwordHash = passwordEncoder.encode("schema-test-password");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """, OWNER_ID, "owner@example.invalid", passwordHash, "Owner");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """, OTHER_USER_ID, "other@example.invalid", passwordHash, "Other");
        jdbcTemplate.update("""
                INSERT INTO categories (id, user_id, name, type)
                VALUES (?, ?, ?, ?)
                """, CATEGORY_ID, OWNER_ID, "Food", "CHI");
    }

    @Test
    void preservesExactTransactionAmounts() {
        insertTransaction(OWNER_ID);

        BigDecimal amount = jdbcTemplate.queryForObject("""
                SELECT amount FROM transactions WHERE user_id = ? AND category_id = ?
                """, BigDecimal.class, OWNER_ID, CATEGORY_ID);

        assertThat(amount).isEqualByComparingTo(MAXIMUM_AMOUNT);
    }

    @Test
    void rejectsTransactionReferencingAnotherOwnersCategory() {
        assertThatThrownBy(() -> insertTransaction(OTHER_USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBudgetReferencingAnotherOwnersCategory() {
        assertThatThrownBy(() -> insertBudget(OTHER_USER_ID, MONTHLY_PERIOD, MONTHLY_TYPE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesExactLimitsAndSupportsMonthlyAndWeeklyBudgets() {
        insertBudget(OWNER_ID, MONTHLY_PERIOD, MONTHLY_TYPE);
        insertBudget(OWNER_ID, WEEKLY_PERIOD, WEEKLY_TYPE);

        BigDecimal limitAmount = jdbcTemplate.queryForObject("""
                SELECT limit_amount FROM budgets WHERE user_id = ? AND category_id = ? AND period = ?
                """, BigDecimal.class, OWNER_ID, CATEGORY_ID, MONTHLY_PERIOD);
        Integer budgetCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM budgets WHERE user_id = ? AND category_id = ?
                """, Integer.class, OWNER_ID, CATEGORY_ID);

        assertThat(limitAmount).isEqualByComparingTo(MAXIMUM_AMOUNT);
        assertThat(budgetCount).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateBudgetForTheSameOwnerCategoryPeriodAndType() {
        insertBudget(OWNER_ID, MONTHLY_PERIOD, MONTHLY_TYPE);

        assertThatThrownBy(() -> insertBudget(OWNER_ID, MONTHLY_PERIOD, MONTHLY_TYPE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownBudgetType() {
        assertThatThrownBy(() -> insertBudget(OWNER_ID, MONTHLY_PERIOD, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertTransaction(long userId) {
        jdbcTemplate.update("""
                INSERT INTO transactions (user_id, category_id, amount, occurred_on)
                VALUES (?, ?, ?, ?)
                """, userId, CATEGORY_ID, MAXIMUM_AMOUNT, OCCURRED_ON);
    }

    private void insertBudget(long userId, String period, String type) {
        jdbcTemplate.update("""
                INSERT INTO budgets (user_id, category_id, period, type, limit_amount)
                VALUES (?, ?, ?, ?, ?)
                """, userId, CATEGORY_ID, period, type, MAXIMUM_AMOUNT);
    }
}

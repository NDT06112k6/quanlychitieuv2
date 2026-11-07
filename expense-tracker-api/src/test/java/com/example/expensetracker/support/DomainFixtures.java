package com.example.expensetracker.support;

import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetRequest;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.transaction.TransactionRequest;
import com.example.expensetracker.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

public final class DomainFixtures {

    public static final Long USER_ID = 1L;
    public static final Long OTHER_USER_ID = 2L;
    public static final Long CATEGORY_ID = 10L;
    public static final Long RESOURCE_ID = 20L;
    public static final LocalDate DATE = LocalDate.of(2026, 11, 1);
    public static final BigDecimal AMOUNT = new BigDecimal("12.34");

    private DomainFixtures() {
    }

    public static User user() {
        User user = new User("owner@example.invalid", "{bcrypt}test-hash", "Owner");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    public static Category category() {
        Category category = new Category(user(), "Food", CategoryType.CHI);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
        return category;
    }

    public static TransactionRequest transactionRequest() {
        TransactionRequest request = new TransactionRequest(CATEGORY_ID, AMOUNT, DATE, "Lunch");
        return request;
    }

    public static Transaction transaction() {
        Category category = category();
        Transaction transaction = new Transaction(category.getUser(), category, transactionRequest());
        ReflectionTestUtils.setField(transaction, "id", RESOURCE_ID);
        return transaction;
    }

    public static Budget budget(BudgetPeriod period) {
        Category category = category();
        BudgetRequest request = new BudgetRequest(CATEGORY_ID, period, AMOUNT);
        Budget budget = new Budget(category.getUser(), category, request);
        ReflectionTestUtils.setField(budget, "id", RESOURCE_ID);
        return budget;
    }
}

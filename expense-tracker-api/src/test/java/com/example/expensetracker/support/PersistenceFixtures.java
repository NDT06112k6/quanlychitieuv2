package com.example.expensetracker.support;

import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetRequest;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.transaction.TransactionRequest;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

@RequiredArgsConstructor
public class PersistenceFixtures {

    private static final String PASSWORD_HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder()
            .encode("persistence-test-password");
    protected final EntityManager entityManager;

    protected User persistUser(String email) {
        User user = new User(email, PASSWORD_HASH, "Owner");
        entityManager.persist(user);
        return user;
    }

    protected Category persistCategory(User user, CategoryType type) {
        Category category = new Category(user, type.name(), type);
        entityManager.persist(category);
        return category;
    }

    protected Transaction persistTransaction(Category category, BigDecimal amount, LocalDate date) {
        TransactionRequest request = new TransactionRequest(category.getId(), amount, date, null);
        Transaction transaction = new Transaction(category.getUser(), category, request);
        entityManager.persist(transaction);
        return transaction;
    }

    protected Budget persistBudget(Category category, BudgetPeriod period, BigDecimal limit) {
        BudgetRequest request = new BudgetRequest(category.getId(), period, limit);
        Budget budget = new Budget(category.getUser(), category, request);
        entityManager.persist(budget);
        return budget;
    }

    protected Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}

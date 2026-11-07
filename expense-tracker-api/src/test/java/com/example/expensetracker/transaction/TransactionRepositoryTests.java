package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.support.PersistenceFixtures;
import com.example.expensetracker.support.PersistenceTest;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class TransactionRepositoryTests extends PersistenceFixtures {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);
    private static final BigDecimal AMOUNT = new BigDecimal("0.10");
    private final TransactionRepository transactionRepository;

    @Autowired
    TransactionRepositoryTests(EntityManager entityManager, TransactionRepository transactionRepository) {
        super(entityManager);
        this.transactionRepository = transactionRepository;
    }

    @Test
    void loadsDistinctCategoriesAndUserWithOnePageQueryAndOneCountQuery() {
        User owner = persistUser("owner@example.invalid");
        persistTransaction(persistCategory(owner, CategoryType.CHI), AMOUNT, DATE);
        persistTransaction(persistCategory(owner, CategoryType.CHI), AMOUNT, DATE);
        persistTransaction(persistCategory(owner, CategoryType.THU), AMOUNT, DATE);
        Statistics statistics = resetStatistics();

        Page<Transaction> transactions = transactionRepository.findAllByUserId(owner.getId(), PageRequest.of(0, 2));
        entityManager.clear();

        assertThat(transactions.getTotalElements()).isEqualTo(3);
        assertFetchedRelations(transactions);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void filtersByOwnerCategoryTypeAndInclusiveDatesWithoutExtraFetches() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category expense = persistCategory(owner, CategoryType.CHI);
        Transaction matching = persistTransaction(expense, AMOUNT, DATE);
        persistTransaction(expense, AMOUNT, DATE.plusDays(1));
        persistTransaction(persistCategory(owner, CategoryType.THU), AMOUNT, DATE);
        persistTransaction(persistCategory(other, CategoryType.CHI), AMOUNT, DATE);
        Statistics statistics = resetStatistics();
        TransactionFilter filter = new TransactionFilter(expense.getId(), CategoryType.CHI, new DateRange(DATE, DATE));

        Page<Transaction> transactions = transactionRepository.searchByUserId(owner.getId(), filter, PageRequest.of(0, 10));
        entityManager.clear();

        assertThat(transactions).extracting(Transaction::getId).containsExactly(matching.getId());
        assertFetchedRelations(transactions);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void scopesAggregatesAndExcludesIncomeOtherOwnersAndDates() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category expense = persistCategory(owner, CategoryType.CHI);
        persistTransaction(expense, new BigDecimal("0.10"), DATE);
        persistTransaction(expense, new BigDecimal("0.20"), DATE);
        persistTransaction(expense, new BigDecimal("100.00"), DATE.plusDays(1));
        persistTransaction(persistCategory(owner, CategoryType.THU), new BigDecimal("50.00"), DATE);
        persistTransaction(persistCategory(other, CategoryType.CHI), new BigDecimal("70.00"), DATE);
        resetStatistics();
        DateRange range = new DateRange(DATE, DATE);

        BigDecimal total = transactionRepository.sumByUserId(owner.getId(), new TransactionFilter(null, CategoryType.CHI, range));
        List<CategorySpending> grouped = transactionRepository.sumExpensesByCategory(owner.getId(), List.of(expense.getId()), range);

        assertThat(total).isEqualByComparingTo("0.30");
        assertThat(grouped).hasSize(1);
        assertThat(grouped.getFirst().totalExpense()).isEqualByComparingTo("0.30");
    }

    @Test
    void returnsZeroForAnEmptyAggregate() {
        User owner = persistUser("owner@example.invalid");
        resetStatistics();

        BigDecimal total = transactionRepository.sumByUserId(owner.getId(), TransactionFilter.all());

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void scopesUpdatesAndDeletesAndPreservesMoneyAndTimestamp() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category category = persistCategory(owner, CategoryType.CHI);
        Transaction transaction = persistTransaction(category, AMOUNT, DATE);
        resetStatistics();
        TransactionRequest request = new TransactionRequest(category.getId(), new BigDecimal("9999999999999.99"), DATE, "Changed");
        TransactionRepository.TransactionChange change = new TransactionRepository.TransactionChange(category, request);

        assertThat(transactionRepository.findByIdAndUserId(transaction.getId(), other.getId())).isEmpty();
        assertThat(transactionRepository.updateOwned(other.getId(), transaction.getId(), change)).isZero();
        assertThat(transactionRepository.deleteOwned(other.getId(), transaction.getId())).isZero();
        assertThat(transactionRepository.updateOwned(owner.getId(), transaction.getId(), change)).isEqualTo(1);
        Transaction updated = transactionRepository.findByIdAndUserId(transaction.getId(), owner.getId()).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo("9999999999999.99");
        assertThat(updated.getCreatedAt()).isNotNull();
        assertThat(transactionRepository.deleteOwned(owner.getId(), transaction.getId())).isEqualTo(1);
    }

    private void assertFetchedRelations(Page<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            assertThat(Hibernate.isInitialized(transaction.getCategory())).isTrue();
            assertThat(Hibernate.isInitialized(transaction.getUser())).isTrue();
            assertThat(transaction.getCategory().getName()).isNotBlank();
            assertThat(transaction.getUser().getDisplayName()).isEqualTo("Owner");
        }
    }
}

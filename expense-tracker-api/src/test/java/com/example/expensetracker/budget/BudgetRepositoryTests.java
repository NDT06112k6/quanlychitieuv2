package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.support.PersistenceFixtures;
import com.example.expensetracker.support.PersistenceTest;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class BudgetRepositoryTests extends PersistenceFixtures {

    private static final BudgetPeriod MONTH = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private static final BudgetPeriod WEEK = new BudgetPeriod(BudgetType.WEEKLY, "2026-W44");
    private static final BigDecimal LIMIT = new BigDecimal("9999999999999.99");
    private final BudgetRepository budgetRepository;

    @Autowired
    BudgetRepositoryTests(EntityManager entityManager, BudgetRepository budgetRepository) {
        super(entityManager);
        this.budgetRepository = budgetRepository;
    }

    @Test
    void loadsDistinctCategoriesAndUserWithoutAdditionalQueries() {
        User owner = persistUser("owner@example.invalid");
        persistBudget(persistCategory(owner, CategoryType.CHI), MONTH, LIMIT);
        persistBudget(persistCategory(owner, CategoryType.CHI), MONTH, LIMIT);
        persistBudget(persistCategory(owner, CategoryType.CHI), MONTH, LIMIT);
        Statistics statistics = resetStatistics();

        Page<Budget> budgets = budgetRepository.findAllByUserId(owner.getId(), PageRequest.of(0, 2));
        entityManager.clear();

        assertThat(budgets.getTotalElements()).isEqualTo(3);
        assertFetchedRelations(budgets);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void filtersBudgetsByOwnerCategoryPeriodAndType() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category category = persistCategory(owner, CategoryType.CHI);
        Budget matching = persistBudget(category, MONTH, LIMIT);
        persistBudget(category, WEEK, LIMIT);
        persistBudget(persistCategory(other, CategoryType.CHI), MONTH, LIMIT);
        Statistics statistics = resetStatistics();
        BudgetFilter filter = new BudgetFilter(category.getId(), MONTH.type(), MONTH.period());

        Page<Budget> budgets = budgetRepository.searchByUserId(owner.getId(), filter, PageRequest.of(0, 10));
        entityManager.clear();

        assertThat(budgets).extracting(Budget::getId).containsExactly(matching.getId());
        assertFetchedRelations(budgets);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void preventsForeignReadsUpdatesAndDeletes() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category category = persistCategory(owner, CategoryType.CHI);
        Budget budget = persistBudget(category, MONTH, LIMIT);
        resetStatistics();
        BudgetRepository.BudgetKey key = new BudgetRepository.BudgetKey(category.getId(), MONTH);

        assertThat(budgetRepository.findByIdAndUserId(budget.getId(), other.getId())).isEmpty();
        assertThat(budgetRepository.findByUserIdAndKey(other.getId(), key)).isEmpty();
        assertThat(budgetRepository.updateOwnedLimit(other.getId(), budget.getId(), BigDecimal.ONE)).isZero();
        assertThat(budgetRepository.deleteOwned(other.getId(), budget.getId())).isZero();
        Budget owned = budgetRepository.findByUserIdAndKey(owner.getId(), key).orElseThrow();
        assertThat(owned.getLimitAmount()).isEqualByComparingTo(LIMIT);
        assertThat(budgetRepository.updateOwnedLimit(owner.getId(), budget.getId(), BigDecimal.ONE)).isEqualTo(1);
        assertThat(budgetRepository.findByIdAndUserId(budget.getId(), owner.getId()).orElseThrow().getLimitAmount())
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(budgetRepository.deleteOwned(owner.getId(), budget.getId())).isEqualTo(1);
    }

    private void assertFetchedRelations(Page<Budget> budgets) {
        for (Budget budget : budgets) {
            assertThat(Hibernate.isInitialized(budget.getCategory())).isTrue();
            assertThat(Hibernate.isInitialized(budget.getUser())).isTrue();
            assertThat(budget.getCategory().getName()).isNotBlank();
            assertThat(budget.getUser().getDisplayName()).isEqualTo("Owner");
        }
    }
}

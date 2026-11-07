package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.transaction.CategorySpending;
import com.example.expensetracker.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.SQLException;
import org.springframework.test.util.ReflectionTestUtils;

import static com.example.expensetracker.support.DomainFixtures.AMOUNT;
import static com.example.expensetracker.support.DomainFixtures.CATEGORY_ID;
import static com.example.expensetracker.support.DomainFixtures.DATE;
import static com.example.expensetracker.support.DomainFixtures.OTHER_USER_ID;
import static com.example.expensetracker.support.DomainFixtures.RESOURCE_ID;
import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTests {

    private static final BudgetPeriod PERIOD = BudgetPeriod.containing(BudgetType.MONTHLY, DATE);
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private BudgetService budgetService;

    @Test
    void createsBudgetWithLockedOwnedCategory() {
        when(categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID)).thenReturn(DomainFixtures.category());
        when(budgetRepository.findByUserIdAndKey(USER_ID, new BudgetRepository.BudgetKey(CATEGORY_ID, PERIOD)))
                .thenReturn(Optional.empty());
        when(budgetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse response = budgetService.createBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT));

        assertThat(response.limitAmount()).isEqualByComparingTo(AMOUNT);
        verify(categoryService).requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID);
    }

    @Test
    void rejectsDuplicateCreationWithoutUpdatingTheExistingBudget() {
        Budget budget = DomainFixtures.budget(PERIOD);
        when(categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID)).thenReturn(budget.getCategory());
        when(budgetRepository.findByUserIdAndKey(USER_ID, new BudgetRepository.BudgetKey(CATEGORY_ID, PERIOD)))
                .thenReturn(Optional.of(budget));
        assertThatThrownBy(() -> budgetService.createBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT)))
                .isInstanceOf(DuplicateBudgetException.class);
        verify(budgetRepository, never()).updateOwnedLimit(any(), any(), any());
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsForeignCategoryBeforeWritingBudget() {
        when(categoryService.requireExpenseCategoryForUpdate(OTHER_USER_ID, CATEGORY_ID))
                .thenThrow(new AccessDeniedException("Denied"));

        assertThatThrownBy(() -> budgetService.createBudget(OTHER_USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesOnlyTheOwnedBudgetLimit() {
        Budget budget = DomainFixtures.budget(PERIOD);
        when(budgetRepository.updateOwnedLimit(USER_ID, RESOURCE_ID, AMOUNT)).thenReturn(1);
        when(budgetRepository.findByIdAndUserId(RESOURCE_ID, USER_ID)).thenReturn(Optional.of(budget));

        BudgetResponse response = budgetService.updateBudgetLimit(USER_ID, RESOURCE_ID, new BudgetLimitRequest(AMOUNT));

        assertThat(response.period()).isEqualTo(PERIOD);
        assertThat(response.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(response.limitAmount()).isEqualByComparingTo(AMOUNT);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUpdatingForeignOrMissingBudget() {
        when(budgetRepository.updateOwnedLimit(OTHER_USER_ID, RESOURCE_ID, AMOUNT)).thenReturn(0);

        assertThatThrownBy(() -> budgetService.updateBudgetLimit(OTHER_USER_ID, RESOURCE_ID, new BudgetLimitRequest(AMOUNT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(budgetRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void translatesDatabaseUniquenessConflictWithoutHidingOtherFailures() {
        when(categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID)).thenReturn(DomainFixtures.category());
        org.hibernate.exception.ConstraintViolationException conflict = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate", new SQLException("Duplicate", "23000", 1062), "uk_budgets_user_category_period_type");
        when(budgetRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate", conflict));

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT)))
                .isInstanceOf(DuplicateBudgetException.class);
    }

    @Test
    void propagatesUnrelatedDatabaseFailure() {
        when(categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID)).thenReturn(DomainFixtures.category());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Other constraint");
        when(budgetRepository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT)))
                .isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00", "0.001", "10000000000000.00"})
    void rejectsLimitsThatCannotBeStoredExactly(String value) {
        assertThatThrownBy(() -> new BudgetLimitRequest(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deniesReadingAnotherUsersBudget() {
        when(budgetRepository.findByIdAndUserId(RESOURCE_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBudget(OTHER_USER_ID, RESOURCE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesDeletingAnotherUsersBudget() {
        when(budgetRepository.deleteOwned(OTHER_USER_ID, RESOURCE_ID)).thenReturn(0);

        assertThatThrownBy(() -> budgetService.deleteBudget(OTHER_USER_ID, RESOURCE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void calculatesAlertsWithOneGroupedQueryForThePage() {
        Budget first = budgetForCategory(CATEGORY_ID);
        Budget second = budgetForCategory(CATEGORY_ID + 1);
        List<Long> categoryIds = List.of(CATEGORY_ID, CATEGORY_ID + 1);
        when(budgetRepository.searchByUserId(eq(USER_ID), eq(BudgetFilter.forPeriod(PERIOD)), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(transactionRepository.sumExpensesByCategory(USER_ID, categoryIds, PERIOD.dateRange()))
                .thenReturn(List.of(new CategorySpending(CATEGORY_ID, new BigDecimal("15.00"))));

        Page<BudgetAlert> alerts = budgetService.getBudgetAlerts(USER_ID, PERIOD, PageRequest.of(0, 10));

        assertThat(alerts.getContent()).extracting(BudgetAlert::status)
                .containsExactly(BudgetStatus.EXCEEDED, BudgetStatus.WITHIN_LIMIT);
        assertThat(alerts.getContent().getFirst().exceededAmount()).isEqualByComparingTo("2.66");
        assertThat(alerts.getContent().getLast().spent()).isEqualByComparingTo("0.00");
        verify(transactionRepository).sumExpensesByCategory(USER_ID, categoryIds, PERIOD.dateRange());
    }

    @Test
    void doesNotAggregateAnEmptyBudgetPage() {
        when(budgetRepository.searchByUserId(eq(USER_ID), any(), any())).thenReturn(Page.empty());

        Page<BudgetAlert> alerts = budgetService.getBudgetAlerts(USER_ID, PERIOD, PageRequest.of(0, 10));

        assertThat(alerts).isEmpty();
        verify(transactionRepository, never()).sumExpensesByCategory(any(), any(), any());
    }

    @Test
    void propagatesAggregationFailuresInsteadOfReportingZeroSpending() {
        when(budgetRepository.searchByUserId(eq(USER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(DomainFixtures.budget(PERIOD))));
        when(transactionRepository.sumExpensesByCategory(eq(USER_ID), any(), any()))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> budgetService.getBudgetAlerts(USER_ID, PERIOD, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");
    }

    private Budget budgetForCategory(Long categoryId) {
        Category category = new Category(DomainFixtures.user(), "Food", CategoryType.CHI);
        ReflectionTestUtils.setField(category, "id", categoryId);
        BudgetRequest request = new BudgetRequest(categoryId, PERIOD, AMOUNT);
        Budget budget = new Budget(category.getUser(), category, request);
        return budget;
    }
}

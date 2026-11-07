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
        when(budgetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse response = budgetService.setBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT));

        assertThat(response.limitAmount()).isEqualByComparingTo(AMOUNT);
        verify(categoryService).requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID);
    }

    @Test
    void updatesExistingBudgetWithoutInsertingDuplicate() {
        Budget budget = DomainFixtures.budget(PERIOD);
        when(categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID)).thenReturn(budget.getCategory());
        when(budgetRepository.findByUserIdAndKey(USER_ID, new BudgetRepository.BudgetKey(CATEGORY_ID, PERIOD)))
                .thenReturn(Optional.of(budget));
        when(budgetRepository.updateOwnedLimit(USER_ID, RESOURCE_ID, AMOUNT)).thenReturn(1);
        when(budgetRepository.findByIdAndUserId(RESOURCE_ID, USER_ID)).thenReturn(Optional.of(budget));

        budgetService.setBudget(USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT));

        verify(budgetRepository).updateOwnedLimit(USER_ID, RESOURCE_ID, AMOUNT);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void rejectsForeignCategoryBeforeWritingBudget() {
        when(categoryService.requireExpenseCategoryForUpdate(OTHER_USER_ID, CATEGORY_ID))
                .thenThrow(new AccessDeniedException("Denied"));

        assertThatThrownBy(() -> budgetService.setBudget(OTHER_USER_ID, new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(budgetRepository, never()).save(any());
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

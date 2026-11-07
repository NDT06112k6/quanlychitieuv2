package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.support.DomainFixtures;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

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
class TransactionServiceTests {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryService categoryService;
    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createsTransactionWithOwnedRelationsAndExactMoney() {
        Category category = DomainFixtures.category();
        when(categoryService.requireOwnedCategory(USER_ID, CATEGORY_ID)).thenReturn(category);
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(USER_ID, DomainFixtures.transactionRequest());

        assertThat(response.amount()).isEqualByComparingTo("12.34");
        assertThat(response.categoryName()).isEqualTo("Food");
    }

    @Test
    void rejectsForeignCategoryBeforeCreatingTransaction() {
        when(categoryService.requireOwnedCategory(OTHER_USER_ID, CATEGORY_ID))
                .thenThrow(new AccessDeniedException("Denied"));

        assertThatThrownBy(() -> transactionService.createTransaction(OTHER_USER_ID, DomainFixtures.transactionRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deniesReadingAnotherUsersTransaction() {
        when(transactionRepository.findByIdAndUserId(RESOURCE_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(OTHER_USER_ID, RESOURCE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesUpdatingAnotherUsersTransactionBeforeCheckingCategory() {
        when(transactionRepository.findByIdAndUserId(RESOURCE_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.updateTransaction(
                OTHER_USER_ID, RESOURCE_ID, DomainFixtures.transactionRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(categoryService, never()).requireOwnedCategory(any(), any());
        verify(transactionRepository, never()).updateOwned(any(), any(), any());
    }

    @Test
    void rejectsMovingOwnedTransactionToForeignCategory() {
        when(transactionRepository.findByIdAndUserId(RESOURCE_ID, USER_ID))
                .thenReturn(Optional.of(DomainFixtures.transaction()));
        when(categoryService.requireOwnedCategory(USER_ID, CATEGORY_ID)).thenThrow(new AccessDeniedException("Denied"));

        assertThatThrownBy(() -> transactionService.updateTransaction(USER_ID, RESOURCE_ID, DomainFixtures.transactionRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).updateOwned(any(), any(), any());
    }

    @Test
    void deniesDeletingAnotherUsersTransaction() {
        when(transactionRepository.deleteOwned(OTHER_USER_ID, RESOURCE_ID)).thenReturn(0);

        assertThatThrownBy(() -> transactionService.deleteTransaction(OTHER_USER_ID, RESOURCE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void calculatesDashboardUsingExactUserScopedDateRangeTotals() {
        DateRange range = new DateRange(DATE.withDayOfMonth(1), DATE.withDayOfMonth(30));
        when(transactionRepository.sumByUserId(USER_ID, new TransactionFilter(null, CategoryType.THU, range)))
                .thenReturn(new BigDecimal("10.30"));
        when(transactionRepository.sumByUserId(USER_ID, new TransactionFilter(null, CategoryType.CHI, range)))
                .thenReturn(new BigDecimal("12.40"));

        DashboardResponse dashboard = transactionService.getDashboard(USER_ID, range);

        assertThat(dashboard.totalIncome()).isEqualByComparingTo("10.30");
        assertThat(dashboard.totalExpense()).isEqualByComparingTo("12.40");
        assertThat(dashboard.balance()).isEqualByComparingTo("-2.10");
    }

    @Test
    void capsPaginationAndPassesOwnerFiltersToRepository() {
        TransactionFilter filter = TransactionFilter.all();
        when(transactionRepository.searchByUserId(eq(USER_ID), eq(filter), any())).thenReturn(Page.empty());

        transactionService.getTransactions(USER_ID, filter, PageRequest.of(2, 1000));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).searchByUserId(eq(USER_ID), eq(filter), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void propagatesDashboardQueryFailure() {
        when(transactionRepository.sumByUserId(eq(USER_ID), any()))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> transactionService.getDashboard(USER_ID, new DateRange(DATE, DATE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");
    }
}

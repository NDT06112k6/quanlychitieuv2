package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.common.util.AccessGuard;
import com.example.expensetracker.common.util.ConstraintViolationUtil;
import com.example.expensetracker.common.util.DomainValidation;
import com.example.expensetracker.common.util.PageableUtil;
import com.example.expensetracker.transaction.CategorySpending;
import com.example.expensetracker.transaction.TransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private static final String UNIQUE_BUDGET_CONSTRAINT = "uk_budgets_user_category_period_type";
    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;

    @Transactional
    public BudgetResponse createBudget(@NotNull @Positive Long userId, @Valid @NotNull BudgetRequest request) {
        // Serialize creation per category; the database also guards against concurrent alternate writers.
        Category category = categoryService.requireExpenseCategoryForUpdate(userId, request.categoryId());
        BudgetRepository.BudgetKey key = new BudgetRepository.BudgetKey(category.getId(), request.period());
        if (budgetRepository.findByUserIdAndKey(userId, key).isPresent()) {
            throw duplicateBudget();
        }
        Budget budget = new Budget(category.getUser(), category, request);
        Budget saved = saveNewBudget(budget);
        BudgetResponse response = BudgetResponse.from(saved);
        return response;
    }

    @Transactional
    public BudgetResponse updateBudgetLimit(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId,
            @Valid @NotNull BudgetLimitRequest request) {
        int affectedRows = budgetRepository.updateOwnedLimit(userId, budgetId, request.limitAmount());
        AccessGuard.requireSuccessfulWrite(affectedRows);
        BudgetResponse response = getBudget(userId, budgetId);
        return response;
    }

    private Budget saveNewBudget(Budget budget) {
        try {
            Budget saved = budgetRepository.saveAndFlush(budget);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolationUtil.matches(exception, UNIQUE_BUDGET_CONSTRAINT)) {
                throw duplicateBudget();
            }
            throw exception;
        }
    }

    private DuplicateBudgetException duplicateBudget() {
        log.warn("Budget creation rejected because the category and period already have a budget.");
        DuplicateBudgetException exception = new DuplicateBudgetException();
        return exception;
    }

    public BudgetResponse getBudget(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId) {
        Budget budget = AccessGuard.requireOwned(budgetRepository.findByIdAndUserId(budgetId, userId));
        BudgetResponse response = BudgetResponse.from(budget);
        return response;
    }

    public Page<BudgetResponse> getBudgets(@NotNull @Positive Long userId,
            @NotNull BudgetFilter filter, @NotNull Pageable pageable) {
        if (filter.categoryId() != null) {
            categoryService.requireOwnedCategory(userId, filter.categoryId());
        }
        Pageable boundedPageable = PageableUtil.bounded(pageable, PageableUtil.BUDGET_SORT_FIELDS);
        Page<Budget> budgets = budgetRepository.searchByUserId(userId, filter, boundedPageable);
        Page<BudgetResponse> responses = budgets.map(BudgetResponse::from);
        return responses;
    }

    @Transactional
    public void deleteBudget(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId) {
        int affectedRows = budgetRepository.deleteOwned(userId, budgetId);
        AccessGuard.requireSuccessfulWrite(affectedRows);
    }

    public Page<BudgetAlert> getBudgetAlerts(@NotNull @Positive Long userId,
            @NotNull BudgetPeriod period, @NotNull Pageable pageable) {
        Pageable boundedPageable = PageableUtil.bounded(pageable, PageableUtil.BUDGET_SORT_FIELDS);
        Page<Budget> budgets = budgetRepository.searchByUserId(userId, BudgetFilter.forPeriod(period), boundedPageable);
        Map<Long, BigDecimal> spending = loadSpending(userId, budgets, period);
        Page<BudgetAlert> alerts = budgets.map(budget -> createAlert(budget, spending));
        return alerts;
    }

    private Map<Long, BigDecimal> loadSpending(Long userId, Page<Budget> budgets, BudgetPeriod period) {
        Map<Long, BigDecimal> spending = new HashMap<>();
        if (budgets.isEmpty()) {
            return spending;
        }
        List<Long> categoryIds = new ArrayList<>();
        for (Budget budget : budgets) {
            categoryIds.add(budget.getCategory().getId());
        }
        List<CategorySpending> totals = transactionRepository.sumExpensesByCategory(userId, categoryIds, period.dateRange());
        for (CategorySpending total : totals) {
            spending.put(total.categoryId(), total.totalExpense());
        }
        return spending;
    }

    private BudgetAlert createAlert(Budget budget, Map<Long, BigDecimal> spending) {
        BigDecimal spent = spending.getOrDefault(budget.getCategory().getId(), DomainValidation.ZERO_AMOUNT);
        BudgetAlert alert = BudgetAlert.from(budget, spent);
        if (alert.status() == BudgetStatus.EXCEEDED) {
            log.warn("Budget spending exceeds its configured limit.");
        }
        return alert;
    }
}

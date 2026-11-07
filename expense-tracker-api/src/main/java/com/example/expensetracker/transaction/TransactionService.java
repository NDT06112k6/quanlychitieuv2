package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.AccessGuard;
import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.common.util.DomainValidation;
import com.example.expensetracker.common.util.PageableUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    @Transactional
    public TransactionResponse createTransaction(@NotNull @Positive Long userId,
            @Valid @NotNull TransactionRequest request) {
        Category category = categoryService.requireOwnedCategory(userId, request.categoryId());
        Transaction transaction = new Transaction(category.getUser(), category, request);
        Transaction saved = transactionRepository.save(transaction);
        TransactionResponse response = TransactionResponse.from(saved);
        return response;
    }

    public TransactionResponse getTransaction(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId) {
        Transaction transaction = AccessGuard.requireOwned(
                transactionRepository.findByIdAndUserId(transactionId, userId));
        TransactionResponse response = TransactionResponse.from(transaction);
        return response;
    }

    public Page<TransactionResponse> getTransactions(@NotNull @Positive Long userId,
            @NotNull TransactionFilter filter, @NotNull Pageable pageable) {
        if (filter.categoryId() != null) {
            categoryService.requireOwnedCategory(userId, filter.categoryId());
        }
        Pageable boundedPageable = PageableUtil.bounded(pageable);
        Page<Transaction> transactions = transactionRepository.searchByUserId(userId, filter, boundedPageable);
        Page<TransactionResponse> responses = transactions.map(TransactionResponse::from);
        return responses;
    }

    @Transactional
    public TransactionResponse updateTransaction(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId,
            @Valid @NotNull TransactionRequest request) {
        getTransaction(userId, transactionId);
        Category category = categoryService.requireOwnedCategory(userId, request.categoryId());
        TransactionRepository.TransactionChange change = new TransactionRepository.TransactionChange(category, request);
        int affectedRows = transactionRepository.updateOwned(userId, transactionId, change);
        AccessGuard.requireSuccessfulWrite(affectedRows);
        TransactionResponse response = getTransaction(userId, transactionId);
        return response;
    }

    @Transactional
    public void deleteTransaction(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId) {
        int affectedRows = transactionRepository.deleteOwned(userId, transactionId);
        AccessGuard.requireSuccessfulWrite(affectedRows);
    }

    public DashboardResponse getDashboard(@NotNull @Positive Long userId, @NotNull DateRange range) {
        TransactionFilter incomeFilter = new TransactionFilter(null, CategoryType.THU, range);
        TransactionFilter expenseFilter = new TransactionFilter(null, CategoryType.CHI, range);
        BigDecimal income = transactionRepository.sumByUserId(userId, incomeFilter)
                .setScale(DomainValidation.MONEY_SCALE);
        BigDecimal expense = transactionRepository.sumByUserId(userId, expenseFilter)
                .setScale(DomainValidation.MONEY_SCALE);
        BigDecimal balance = income.subtract(expense);
        DashboardResponse response = new DashboardResponse(income, expense, balance);
        return response;
    }
}

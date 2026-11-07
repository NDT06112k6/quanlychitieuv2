package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(Long id, Long categoryId, String categoryName, CategoryType type,
        BigDecimal amount, LocalDate occurredOn, String note, LocalDateTime createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        TransactionResponse response = new TransactionResponse(transaction.getId(), transaction.getCategory().getId(),
                transaction.getCategory().getName(), transaction.getCategory().getType(), transaction.getAmount(),
                transaction.getOccurredOn(), transaction.getNote(), transaction.getCreatedAt());
        return response;
    }
}

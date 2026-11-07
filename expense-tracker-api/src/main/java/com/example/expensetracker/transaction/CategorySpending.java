package com.example.expensetracker.transaction;

import java.math.BigDecimal;

public record CategorySpending(Long categoryId, BigDecimal totalExpense) {
}

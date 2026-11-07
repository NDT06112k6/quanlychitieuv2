package com.example.expensetracker.transaction;

import java.math.BigDecimal;

public record DashboardResponse(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance) {
}

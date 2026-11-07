package com.example.expensetracker.budget;

import com.example.expensetracker.common.exception.ErrorMessages;

public class DuplicateBudgetException extends RuntimeException {

    public DuplicateBudgetException() {
        super(ErrorMessages.DUPLICATE_BUDGET);
    }
}

package com.example.expensetracker.common.exception;

public record FieldValidationError(String field, String message) {
}

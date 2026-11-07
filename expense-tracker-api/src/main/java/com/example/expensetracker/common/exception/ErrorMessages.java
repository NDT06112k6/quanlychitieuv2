package com.example.expensetracker.common.exception;

public final class ErrorMessages {

    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String EMAIL_ALREADY_EXISTS = "Email is already registered";
    public static final String DUPLICATE_BUDGET = "Budget already exists for this category, period and type";
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required";
    public static final String ACCESS_DENIED = "Access is denied";
    public static final String VALIDATION_FAILED = "Request validation failed";
    public static final String INVALID_REQUEST = "Invalid request";
    public static final String INTERNAL_ERROR = "An unexpected error occurred";
    public static final String RESOURCE_IN_USE = "Resource is referenced by existing data";

    private ErrorMessages() {
    }
}

package com.example.expensetracker.common.web;

public final class ApiDocumentation {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String PAGINATION = "Pages are zero-based and capped at 100 rows. Default sort is id,desc; whitelisted fields support asc/desc. Repeat sort for multiple fields.";

    private ApiDocumentation() {
    }
}

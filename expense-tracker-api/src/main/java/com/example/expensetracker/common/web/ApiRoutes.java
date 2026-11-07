package com.example.expensetracker.common.web;

public final class ApiRoutes {

    public static final String BASE = "/api/v1";
    public static final String USER_ME = BASE + "/users/me";
    public static final String USER_PASSWORD = USER_ME + "/password";
    public static final String CATEGORIES = BASE + "/categories";
    public static final String TRANSACTIONS = BASE + "/transactions";
    public static final String BUDGETS = BASE + "/budgets";
    public static final String DASHBOARD = "/dashboard";
    public static final String ALERTS = "/alerts";
    public static final String ID = "id";
    public static final String ITEM = "/{" + ID + "}";

    private ApiRoutes() {
    }
}

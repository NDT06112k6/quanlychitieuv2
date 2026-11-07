package com.example.expensetracker.auth;

public final class AuthRoutes {

    public static final String BASE = "/api/v1/auth";
    public static final String REGISTER = BASE + "/register";
    public static final String LOGIN = BASE + "/login";
    public static final String API_DOCS = "/v3/api-docs/**";
    public static final String SWAGGER_UI = "/swagger-ui/**";
    public static final String SWAGGER_ENTRY = "/swagger-ui.html";

    private AuthRoutes() {
    }
}

package com.example.expensetracker.auth;

public final class AuthConstants {

    public static final String TOKEN_TYPE = "Bearer";
    public static final String BEARER_PREFIX = TOKEN_TYPE + " ";
    public static final String ROLE_CLAIM = "role";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final int MINIMUM_PASSWORD_LENGTH = 8;
    public static final int MAXIMUM_PASSWORD_BYTES = 72;
    public static final int MAXIMUM_EMAIL_LENGTH = 255;

    private AuthConstants() {
    }
}

package com.example.expensetracker.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {

    @Override
    public String toString() {
        String description = "AuthResponse[redacted]";
        return description;
    }
}

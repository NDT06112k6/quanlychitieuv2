package com.example.expensetracker.user;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {

    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int MAXIMUM_PASSWORD_BYTES = 72;

    public PasswordChangeRequest {
        // BCrypt limits UTF-8 bytes, so character-only validation would accept oversized passwords.
        requirePassword(currentPassword);
        requirePassword(newPassword);
        if (newPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password is too short");
        }
    }

    private static void requirePassword(String password) {
        DomainValidation.requireText(password, MAXIMUM_PASSWORD_BYTES);
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAXIMUM_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password exceeds the encoding limit");
        }
    }

    @Override
    public String toString() {
        String description = "PasswordChangeRequest[redacted]";
        return description;
    }
}

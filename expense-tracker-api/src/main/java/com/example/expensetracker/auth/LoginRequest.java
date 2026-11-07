package com.example.expensetracker.auth;

import com.example.expensetracker.auth.validation.Utf8Password;
import com.example.expensetracker.common.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = AuthConstants.MAXIMUM_EMAIL_LENGTH) String email,
        @NotBlank @Size(max = AuthConstants.MAXIMUM_PASSWORD_BYTES) @Utf8Password String password) {

    public LoginRequest {
        email = EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
        String description = "LoginRequest[redacted]";
        return description;
    }
}

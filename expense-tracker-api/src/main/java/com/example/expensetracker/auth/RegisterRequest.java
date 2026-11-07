package com.example.expensetracker.auth;

import com.example.expensetracker.auth.validation.Utf8Password;
import com.example.expensetracker.common.util.EmailNormalizer;
import com.example.expensetracker.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = AuthConstants.MAXIMUM_EMAIL_LENGTH) String email,
        @NotBlank @Size(min = AuthConstants.MINIMUM_PASSWORD_LENGTH,
                max = AuthConstants.MAXIMUM_PASSWORD_BYTES) @Utf8Password String password,
        @NotBlank @Size(max = User.MAXIMUM_NAME_LENGTH) String displayName) {

    public RegisterRequest {
        email = EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
        String description = "RegisterRequest[redacted]";
        return description;
    }
}

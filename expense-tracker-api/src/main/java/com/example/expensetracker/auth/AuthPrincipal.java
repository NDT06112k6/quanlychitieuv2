package com.example.expensetracker.auth;

import com.example.expensetracker.common.util.DomainValidation;
import com.example.expensetracker.user.UserRole;
import java.security.Principal;
import java.util.Objects;

public record AuthPrincipal(Long userId, UserRole role) implements Principal {

    public AuthPrincipal {
        DomainValidation.requirePositiveId(userId);
        Objects.requireNonNull(role, "Role is required");
    }

    @Override
    public String getName() {
        String name = userId.toString();
        return name;
    }
}

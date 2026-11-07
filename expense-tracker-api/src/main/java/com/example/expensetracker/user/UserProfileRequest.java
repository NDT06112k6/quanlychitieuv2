package com.example.expensetracker.user;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(@NotBlank @Size(max = User.MAXIMUM_NAME_LENGTH) String displayName) {

    public UserProfileRequest {
        displayName = DomainValidation.requireText(displayName, User.MAXIMUM_NAME_LENGTH);
    }
}

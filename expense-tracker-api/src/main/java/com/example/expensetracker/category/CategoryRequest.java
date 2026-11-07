package com.example.expensetracker.category;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public record CategoryRequest(@NotBlank @Size(max = Category.MAXIMUM_NAME_LENGTH) String name, @NotNull CategoryType type) {

    public CategoryRequest {
        name = DomainValidation.requireText(name, Category.MAXIMUM_NAME_LENGTH);
        Objects.requireNonNull(type, "Category type is required");
    }
}

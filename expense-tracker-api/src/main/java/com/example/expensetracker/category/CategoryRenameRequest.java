package com.example.expensetracker.category;

import com.example.expensetracker.common.util.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRenameRequest(@NotBlank @Size(max = Category.MAXIMUM_NAME_LENGTH) String name) {

    public CategoryRenameRequest {
        name = DomainValidation.requireText(name, Category.MAXIMUM_NAME_LENGTH);
    }
}

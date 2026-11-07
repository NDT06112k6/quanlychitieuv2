package com.example.expensetracker.category;

public record CategoryResponse(Long id, String name, CategoryType type) {

    public static CategoryResponse from(Category category) {
        CategoryResponse response = new CategoryResponse(category.getId(), category.getName(), category.getType());
        return response;
    }
}

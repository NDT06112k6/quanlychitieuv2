package com.example.expensetracker.user;

public record UserResponse(Long id, String email, String displayName, UserRole role) {

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
        return response;
    }
}

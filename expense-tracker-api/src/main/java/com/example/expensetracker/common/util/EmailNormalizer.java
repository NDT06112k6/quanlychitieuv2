package com.example.expensetracker.common.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        return normalized;
    }
}

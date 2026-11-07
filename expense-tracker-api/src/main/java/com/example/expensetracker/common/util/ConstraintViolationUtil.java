package com.example.expensetracker.common.util;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;

public final class ConstraintViolationUtil {

    private ConstraintViolationUtil() {
    }

    public static boolean matches(Throwable exception, String expectedConstraint) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String constraintName = violation.getConstraintName().toLowerCase(Locale.ROOT);
                if (constraintName.contains(expectedConstraint)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}

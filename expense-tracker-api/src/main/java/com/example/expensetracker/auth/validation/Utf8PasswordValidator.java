package com.example.expensetracker.auth.validation;

import com.example.expensetracker.auth.AuthConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class Utf8PasswordValidator implements ConstraintValidator<Utf8Password, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        boolean valid = password.getBytes(StandardCharsets.UTF_8).length <= AuthConstants.MAXIMUM_PASSWORD_BYTES;
        return valid;
    }
}

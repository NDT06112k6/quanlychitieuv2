package com.example.expensetracker.auth;

import com.example.expensetracker.common.exception.ErrorMessages;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super(ErrorMessages.EMAIL_ALREADY_EXISTS);
    }
}

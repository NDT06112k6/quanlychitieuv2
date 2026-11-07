package com.example.expensetracker.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ApiErrorResponse(int status, String error, String message, Instant timestamp, String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldValidationError> fieldErrors) {

    public static ApiErrorResponse create(HttpStatus status, String message, String path) {
        ApiErrorResponse response = new ApiErrorResponse(status.value(), status.getReasonPhrase(),
                message, Instant.now(), path, List.of());
        return response;
    }

    public static ApiErrorResponse validation(List<FieldValidationError> errors, String path) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiErrorResponse response = new ApiErrorResponse(status.value(), status.getReasonPhrase(),
                ErrorMessages.VALIDATION_FAILED, Instant.now(), path, List.copyOf(errors));
        return response;
    }
}

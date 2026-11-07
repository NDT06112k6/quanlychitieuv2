package com.example.expensetracker.common.exception;

import com.example.expensetracker.auth.EmailAlreadyExistsException;
import com.example.expensetracker.budget.DuplicateBudgetException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldValidationError> errors = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.add(new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        ApiErrorResponse error = ApiErrorResponse.validation(errors, request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = ResponseEntity.badRequest().body(error);
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraints(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldValidationError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.add(new FieldValidationError(violation.getPropertyPath().toString(), violation.getMessage()));
        }
        ApiErrorResponse error = ApiErrorResponse.validation(errors, request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = ResponseEntity.badRequest().body(error);
        return response;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.FORBIDDEN, ErrorMessages.ACCESS_DENIED, request);
        return response;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(EmailAlreadyExistsException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.CONFLICT, ErrorMessages.EMAIL_ALREADY_EXISTS, request);
        return response;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(BadCredentialsException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_CREDENTIALS, request);
        return response;
    }

    @ExceptionHandler(DuplicateBudgetException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateBudget(DuplicateBudgetException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.CONFLICT, ErrorMessages.DUPLICATE_BUDGET, request);
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(Exception exception, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_REQUEST, request);
        return response;
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiErrorResponse> handleWebError(Exception exception, HttpServletRequest request) {
        ErrorResponse webError = (ErrorResponse) exception;
        HttpStatus status = HttpStatus.valueOf(webError.getStatusCode().value());
        ResponseEntity<ApiErrorResponse> response = respond(status, status.getReasonPhrase(), request);
        return response;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        // Persistence/decoder exceptions can contain PII or secrets in their messages and causes.
        RuntimeException safeException = new RuntimeException(exception.getClass().getName());
        safeException.setStackTrace(exception.getStackTrace());
        log.error("Unhandled API exception.", safeException);
        ResponseEntity<ApiErrorResponse> response = respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorMessages.INTERNAL_ERROR, request);
        return response;
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse error = ApiErrorResponse.create(status, message, request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = ResponseEntity.status(status).body(error);
        return response;
    }
}

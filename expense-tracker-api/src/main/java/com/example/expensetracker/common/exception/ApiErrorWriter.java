package com.example.expensetracker.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final JsonMapper jsonMapper;

    public void writeSecurityError(HttpServletRequest request, HttpServletResponse response, HttpStatus status)
            throws IOException {
        String message = ErrorMessages.ACCESS_DENIED;
        if (status == HttpStatus.UNAUTHORIZED) {
            message = ErrorMessages.AUTHENTICATION_REQUIRED;
        }
        ApiErrorResponse error = ApiErrorResponse.create(status, message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), error);
    }
}

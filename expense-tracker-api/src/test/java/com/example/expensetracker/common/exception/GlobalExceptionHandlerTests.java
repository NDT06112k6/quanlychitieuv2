package com.example.expensetracker.common.exception;

import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.budget.DuplicateBudgetException;
import com.example.expensetracker.config.SecurityConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTests.TestEndpoints.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, ApiErrorWriter.class, GlobalExceptionHandlerTests.TestEndpoints.class})
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    private static final String SENSITIVE_DETAIL = "sensitive-internal-detail";
    private static final String CONSTRAINTS = "/test/errors/constraints";
    private static final String DENIED = "/test/errors/denied";
    private static final String UNEXPECTED = "/test/errors/unexpected";
    private static final String DUPLICATE_BUDGET = "/test/errors/duplicate-budget";
    private final MockMvc mockMvc;

    @Autowired
    GlobalExceptionHandlerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void mapsConstraintViolationsTo400WithFieldErrors() throws Exception {
        mockMvc.perform(get(CONSTRAINTS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.message").value(ErrorMessages.VALIDATION_FAILED));
    }

    @Test
    void mapsOwnershipDenialsToGeneric403() throws Exception {
        mockMvc.perform(get(DENIED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.ACCESS_DENIED))
                .andExpect(content().string(not(containsString(SENSITIVE_DETAIL))));
    }

    @Test
    void mapsDuplicateBudgetToUniform409() throws Exception {
        mockMvc.perform(get(DUPLICATE_BUDGET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(ErrorMessages.DUPLICATE_BUDGET))
                .andExpect(jsonPath("$.path").value(DUPLICATE_BUDGET));
    }

    @Test
    void hidesInternalDetailsInResponseAndLogsWhileKeepingStackTrace(CapturedOutput output) throws Exception {
        mockMvc.perform(get(UNEXPECTED))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(ErrorMessages.INTERNAL_ERROR))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value(UNEXPECTED))
                .andExpect(content().string(not(containsString(SENSITIVE_DETAIL))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        assertThat(output).contains("Unhandled API exception", "IllegalStateException", "TestEndpoints.unexpected");
        assertThat(output).doesNotContain(SENSITIVE_DETAIL);
    }

    @RestController
    @RequiredArgsConstructor
    static class TestEndpoints {

        private final Validator validator;

        @GetMapping(DUPLICATE_BUDGET)
        public String duplicateBudget() {
            throw new DuplicateBudgetException();
        }

        @GetMapping(CONSTRAINTS)
        public String constraints() {
            throw new ConstraintViolationException(validator.validate(new ValidatedValue("")));
        }

        @GetMapping(DENIED)
        public String denied() {
            throw new AccessDeniedException(SENSITIVE_DETAIL);
        }

        @GetMapping(UNEXPECTED)
        public String unexpected() {
            throw new IllegalStateException(SENSITIVE_DETAIL);
        }
    }

    record ValidatedValue(@NotBlank String name) {
    }
}

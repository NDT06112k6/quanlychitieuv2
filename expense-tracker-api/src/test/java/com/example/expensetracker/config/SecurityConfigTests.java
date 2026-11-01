package com.example.expensetracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTests {

    private final MockMvc mockMvc;

    @Autowired
    SecurityConfigTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void deniesAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deniesAuthenticatedAccessUntilPermissionsAreDeclared() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deniesMutationsEvenWithValidCsrfToken() throws Exception {
        mockMvc.perform(post("/api/transactions").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectsApiDocumentationByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isForbidden());
    }
}

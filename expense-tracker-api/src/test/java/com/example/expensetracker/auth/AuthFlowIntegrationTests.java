package com.example.expensetracker.auth;

import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import com.example.expensetracker.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTests {

    private static final String EMAIL = "integration@example.invalid";
    private static final String PASSWORD = "integration-password";
    private static final String REGISTER_BODY = """
            {"email":" INTEGRATION@EXAMPLE.INVALID ","password":"integration-password","displayName":"Owner"}
            """;
    private static final String LOGIN_BODY = """
            {"email":" INTEGRATION@EXAMPLE.INVALID ","password":"integration-password"}
            """;
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Autowired
    AuthFlowIntegrationTests(MockMvc mockMvc, UserRepository userRepository, JwtService jwtService) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Test
    void registersPersistsEncodedPasswordAndLogsInWithNormalizedEmail() throws Exception {
        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);

        MvcResult result = mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        assertThat(jwtService.readUserId(token)).isEqualTo(user.getId());
        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void servesRealOpenApiAndSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

package com.example.expensetracker.auth;

import com.example.expensetracker.common.exception.ApiErrorWriter;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, ApiErrorWriter.class})
class AuthControllerTests {

    private static final String REGISTER_BODY = """
            {"email":"owner@example.invalid","password":"test-password","displayName":"Owner"}
            """;
    private static final String LOGIN_BODY = """
            {"email":"owner@example.invalid","password":"test-password"}
            """;
    private static final AuthResponse RESPONSE = new AuthResponse("access-token", AuthConstants.TOKEN_TYPE, 3600);
    @MockitoBean
    private AuthService authService;
    private final MockMvc mockMvc;

    @Autowired
    AuthControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void registersWith201AndOnlyTheResponseDto() throws Exception {
        when(authService.register(any())).thenReturn(RESPONSE);

        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value(RESPONSE.accessToken()))
                .andExpect(jsonPath("$.tokenType").value(AuthConstants.TOKEN_TYPE))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void logsInWithoutCsrfTokenAndReturns200() throws Exception {
        when(authService.login(any())).thenReturn(RESPONSE);

        mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(RESPONSE.accessToken()));
    }

    @Test
    void rejectsInvalidFieldsWithDetailed400Response() throws Exception {
        String invalid = """
                {"email":"invalid","password":"short","displayName":" "}
                """;

        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(AuthRoutes.REGISTER))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("displayName")));
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsMultibytePasswordExceedingBcryptByteLimit() throws Exception {
        String invalid = REGISTER_BODY.replace("test-password", "ệ".repeat(30));

        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsInvalidLoginBody() throws Exception {
        String invalid = """
                {"email":"invalid","password":""}
                """;

        mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void returnsGeneric401ForWrongCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Private authentication detail"));

        mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns409ForExistingEmail() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_REQUEST));
    }
}

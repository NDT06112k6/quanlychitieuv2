package com.example.expensetracker.config;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.AuthPrincipal;
import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.auth.JwtTestFixtures;
import com.example.expensetracker.common.exception.ApiErrorWriter;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTests.TestEndpoints.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, ApiErrorWriter.class, SecurityConfigTests.TestEndpoints.class})
class SecurityConfigTests {

    private static final String PROTECTED = "/test/protected";
    private static final String ADMIN_ONLY = "/test/admin";
    private final MockMvc mockMvc;
    private final JwtService jwtService;

    @Autowired
    SecurityConfigTests(MockMvc mockMvc, JwtService jwtService) {
        this.mockMvc = mockMvc;
        this.jwtService = jwtService;
    }

    @Test
    void returnsUniform401WithoutCredentials() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(ErrorMessages.AUTHENTICATION_REQUIRED))
                .andExpect(jsonPath("$.path").value(PROTECTED))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns401ForInvalidToken() throws Exception {
        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.AUTHENTICATION_REQUIRED));
    }

    @Test
    void returns401ForExpiredToken() throws Exception {
        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION,
                        AuthConstants.BEARER_PREFIX + JwtTestFixtures.expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic ", "Bearer", "Bearer\t", " Bearer "})
    void rejectsMalformedAuthorizationSchemeEvenWithAValidToken(String prefix) throws Exception {
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);
        String authorization = prefix + token;

        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(ErrorMessages.AUTHENTICATION_REQUIRED));
    }

    @Test
    void acceptsVerifiedTokenAndExposesUserIdWithoutCreatingSession() throws Exception {
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);

        MvcResult result = mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(JwtTestFixtures.PRINCIPAL.userId()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        mockMvc.perform(get(PROTECTED)).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedMutationWithoutCsrfToken() throws Exception {
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);

        mockMvc.perform(post(PROTECTED).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + token))
                .andExpect(status().isOk());
    }

    @Test
    void returns403WithTheSameErrorShapeForInsufficientRole() throws Exception {
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);

        mockMvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(ErrorMessages.ACCESS_DENIED))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value(ADMIN_ONLY));
    }

    @Test
    void propagatesTheSignedAdminRole() throws Exception {
        String token = jwtService.createToken(new AuthPrincipal(1L, UserRole.ADMIN));

        mockMvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui.html"})
    void allowsDocumentationWithoutCredentials(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {AuthRoutes.REGISTER, AuthRoutes.LOGIN})
    void allowsPublicAuthEndpointsEvenWithAnInvalidBearerToken(String path) throws Exception {
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + "invalid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotAccidentallyPermitOtherAuthRoutes() throws Exception {
        mockMvc.perform(get(AuthRoutes.BASE + "/private")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {StaticWebSecurity.ROOT, StaticWebSecurity.INDEX,
            "/css/security-test.css", "/js/security-test.js", StaticWebSecurity.FAVICON})
    void allowsOnlyTheExplicitStaticPathsAndWritesTheAppPolicy(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", StaticWebSecurity.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void keepsApiRequestsProtectedAfterMakingTheFrontendPublic() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void doesNotPermitStaticPostRequests() throws Exception {
        mockMvc.perform(post(StaticWebSecurity.ROOT)).andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestEndpoints {

        @GetMapping({StaticWebSecurity.ROOT, StaticWebSecurity.INDEX,
                "/css/security-test.css", "/js/security-test.js", StaticWebSecurity.FAVICON})
        public String appStaticResource() {
            String response = "public app resource";
            return response;
        }

        @GetMapping(PROTECTED)
        public AuthPrincipal protectedEndpoint(@AuthenticationPrincipal AuthPrincipal principal) {
            return principal;
        }

        @PostMapping(PROTECTED)
        public AuthPrincipal mutate(@AuthenticationPrincipal AuthPrincipal principal) {
            return principal;
        }

        @GetMapping(ADMIN_ONLY)
        @PreAuthorize("hasRole('ADMIN')")
        public String adminEndpoint() {
            String response = "admin";
            return response;
        }

        @GetMapping({"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui.html"})
        public String docs() {
            String response = "public documentation";
            return response;
        }

        @PostMapping({AuthRoutes.REGISTER, AuthRoutes.LOGIN})
        public String publicAuthEndpoint() {
            String response = "public authentication endpoint";
            return response;
        }
    }
}

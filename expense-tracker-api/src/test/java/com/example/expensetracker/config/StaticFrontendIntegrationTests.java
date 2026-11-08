package com.example.expensetracker.config;

import com.example.expensetracker.common.web.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticFrontendIntegrationTests {

    private static final String MAIN_MODULE = "/js/app.js";
    private static final String STYLE_SHEET = "/css/style.css";
    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String REFERRER_HEADER = "Referrer-Policy";
    private final MockMvc mockMvc;

    @Autowired
    StaticFrontendIntegrationTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @ValueSource(strings = {StaticWebSecurity.ROOT, StaticWebSecurity.INDEX, MAIN_MODULE, STYLE_SHEET})
    void servesAppResourcesPubliclyWithStrictHeaders(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string(CSP_HEADER, containsString("default-src 'self'")))
                .andExpect(header().string(CSP_HEADER, containsString("script-src 'self'")))
                .andExpect(header().string(CSP_HEADER, containsString("style-src 'self'")))
                .andExpect(header().string(CSP_HEADER, containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(REFERRER_HEADER, "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void allowsHeadForStaticResources() throws Exception {
        mockMvc.perform(head(MAIN_MODULE))
                .andExpect(status().isOk())
                .andExpect(header().string(CSP_HEADER, containsString("default-src 'self'")));
    }

    @Test
    void keepsRealTransactionApiProtectedWithUniform401() throws Exception {
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(ApiRoutes.TRANSACTIONS))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(header().doesNotExist(CSP_HEADER));
        mockMvc.perform(post(ApiRoutes.TRANSACTIONS)).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotMakeUnlistedPathsOrStaticMutationsPublic() throws Exception {
        mockMvc.perform(get("/private-page")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(StaticWebSecurity.ROOT)).andExpect(status().isUnauthorized());
    }

    @Test
    void keepsSwaggerEntryPublicWithoutTheAppPolicy() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/swagger-ui/index.html"))
                .andExpect(header().doesNotExist(CSP_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config"})
    void servesSwaggerResourcesWithoutTheAppPolicy(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(CSP_HEADER))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}

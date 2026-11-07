package com.example.expensetracker.config;

import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.common.web.ApiDocumentation;
import com.example.expensetracker.common.web.ApiRoutes;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTests {

    private static final String DOCS = "/v3/api-docs";
    private final MockMvc mockMvc;

    @Autowired
    OpenApiConfigTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void declaresBearerSchemeAndPublicAuthOperations() throws Exception {
        mockMvc.perform(get(DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Expense Tracker API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['" + AuthRoutes.REGISTER + "'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['" + AuthRoutes.LOGIN + "'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['" + ApiRoutes.USER_ME + "'].get.security[0]." + ApiDocumentation.BEARER_SCHEME).isArray());
    }

    @Test
    void documentsQueryFiltersAndErrorDtosWithoutExposingPrincipalAsInput() throws Exception {
        MvcResult result = mockMvc.perform(get(DOCS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + ApiRoutes.CATEGORIES + "'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + ApiRoutes.CATEGORIES + "/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['" + ApiRoutes.BUDGETS + "'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ApiRoutes.CATEGORIES + "'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['" + ApiRoutes.CATEGORIES + "'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['" + ApiRoutes.CATEGORIES + "'].get.responses['403']").exists())
                .andReturn();

        String specification = result.getResponse().getContentAsString();
        List<String> parameters = JsonPath.read(specification, "$.paths['" + ApiRoutes.TRANSACTIONS + "'].get.parameters[*].name");
        assertThat(parameters).contains("startDate", "endDate", "categoryId", "type", "page", "size", "sort")
                .doesNotContain("userId", "principal", "role");
    }

    @Test
    void documentsRenameAndLimitOnlyRequestBodies() throws Exception {
        mockMvc.perform(get(DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CategoryRenameRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.CategoryRenameRequest.properties.type").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BudgetLimitRequest.properties.limitAmount").exists())
                .andExpect(jsonPath("$.components.schemas.BudgetLimitRequest.properties.categoryId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BudgetLimitRequest.properties.userId").doesNotExist());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

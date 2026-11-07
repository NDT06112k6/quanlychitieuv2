package com.example.expensetracker;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.AuthResponse;
import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.RegisterRequest;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetRequest;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.category.CategoryRequest;
import com.example.expensetracker.category.CategoryResponse;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.transaction.TransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SortingIntegrationTests {

    private static final String PASSWORD = "sorting-test-password";
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 11, 1);
    private static final BudgetPeriod PERIOD = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private static final BigDecimal SMALL_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("30.00");
    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private String authorization;

    @Autowired
    SortingIntegrationTests(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    @BeforeEach
    void createApiFixturesWithinTheTestTransaction() throws Exception {
        RegisterRequest registration = new RegisterRequest("sorting@example.invalid", PASSWORD, "Sorting");
        MvcResult registered = mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated()).andReturn();
        AuthResponse authentication = jsonMapper.readValue(registered.getResponse().getContentAsString(), AuthResponse.class);
        authorization = AuthConstants.BEARER_PREFIX + authentication.accessToken();
        CategoryResponse first = createCategory("Zulu");
        CategoryResponse second = createCategory("Alpha");
        create(ApiRoutes.TRANSACTIONS, new TransactionRequest(first.id(), LARGE_AMOUNT, FIRST_DATE, "First"));
        create(ApiRoutes.TRANSACTIONS, new TransactionRequest(second.id(), SMALL_AMOUNT, FIRST_DATE.plusDays(1), "Second"));
        create(ApiRoutes.TRANSACTIONS, new TransactionRequest(first.id(), SMALL_AMOUNT, FIRST_DATE.plusDays(1), "Third"));
        create(ApiRoutes.BUDGETS, new BudgetRequest(first.id(), PERIOD, LARGE_AMOUNT));
        create(ApiRoutes.BUDGETS, new BudgetRequest(second.id(), PERIOD, SMALL_AMOUNT));
    }

    @ParameterizedTest
    @CsvSource({"asc,Alpha,Zulu", "desc,Zulu,Alpha"})
    void sortsCategoriesByNameInTheDatabase(String direction, String first, String second) throws Exception {
        List<String> names = listValues(ApiRoutes.CATEGORIES, "name," + direction, "name");
        assertThat(names).containsExactly(first, second);
    }

    @ParameterizedTest
    @CsvSource({
            "amount,asc,Third Second First",
            "amount,desc,First Third Second",
            "occurredOn,asc,First Third Second",
            "occurredOn,desc,Third Second First",
            "id,asc,First Second Third",
            "id,desc,Third Second First"
    })
    void sortsTransactionsAndBreaksTiesByDescendingId(String field, String direction, String expectedOrder) throws Exception {
        List<String> notes = listValues(ApiRoutes.TRANSACTIONS, field + "," + direction, "note");
        String[] expectedNotes = expectedOrder.split(" ");
        assertThat(notes).containsExactly(expectedNotes);
    }

    @ParameterizedTest
    @CsvSource({"asc,Alpha,Zulu", "desc,Zulu,Alpha"})
    void sortsBudgetsByLimitInTheDatabase(String direction, String first, String second) throws Exception {
        List<String> categories = listValues(ApiRoutes.BUDGETS, "limitAmount," + direction, "categoryName");
        assertThat(categories).containsExactly(first, second);
    }

    @ParameterizedTest
    @ValueSource(strings = {ApiRoutes.CATEGORIES, ApiRoutes.TRANSACTIONS, ApiRoutes.BUDGETS})
    void retainsDefaultDescendingIdWhenSortIsOmitted(String path) throws Exception {
        JsonNode page = readPage(mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andReturn());
        long previousId = Long.MAX_VALUE;
        for (JsonNode resource : page.get("content")) {
            long id = resource.get("id").asLong();
            assertThat(id).isLessThan(previousId);
            previousId = id;
        }
    }

    @Test
    void appliesMultipleSortsBeforePagination() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("sort", "amount,asc", "occurredOn,desc").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = readPage(result);
        assertThat(page.get("content").get(0).get("note").asString()).isEqualTo("Second");
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
    }

    private CategoryResponse createCategory(String name) throws Exception {
        MvcResult result = create(ApiRoutes.CATEGORIES, new CategoryRequest(name, CategoryType.CHI));
        CategoryResponse category = jsonMapper.readValue(result.getResponse().getContentAsString(), CategoryResponse.class);
        return category;
    }

    private MvcResult create(String path, Object request) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        return result;
    }

    private List<String> listValues(String path, String sort, String field) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization).param("sort", sort))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = readPage(result);
        List<String> values = new ArrayList<>();
        for (JsonNode resource : page.get("content")) {
            values.add(resource.get(field).asString());
        }
        return values;
    }

    private JsonNode readPage(MvcResult result) throws Exception {
        JsonNode page = jsonMapper.readTree(result.getResponse().getContentAsString());
        return page;
    }
}

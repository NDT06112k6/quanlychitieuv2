package com.example.expensetracker;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.AuthResponse;
import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.RegisterRequest;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetResponse;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.category.CategoryRequest;
import com.example.expensetracker.category.CategoryResponse;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.transaction.TransactionResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrontendMoneyContractIntegrationTests {

    private static final String PASSWORD = "frontend-contract-password";
    private static final String MAXIMUM_AMOUNT = "9999999999999.99";
    private static final String DECIMAL_AMOUNT = "125000.50";
    private static final String TRANSACTION_DATE = "2026-11-08";
    private static final BudgetPeriod PERIOD = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    @Autowired
    FrontendMoneyContractIntegrationTests(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    @ParameterizedTest
    @ValueSource(strings = {DECIMAL_AMOUNT, MAXIMUM_AMOUNT})
    void storesTransactionAmountsSentAsJsonStringsWithoutRounding(String amount) throws Exception {
        Actor actor = registerAndCreateCategory();
        Map<String, Object> request = Map.of("categoryId", actor.categoryId(), "amount", amount,
                "occurredOn", TRANSACTION_DATE, "note", "Decimal string request");

        MvcResult result = create(ApiRoutes.TRANSACTIONS, actor, request);
        TransactionResponse transaction = readBody(result, TransactionResponse.class);

        assertThat(transaction.amount()).isEqualByComparingTo(amount);
        MvcResult stored = mockMvc.perform(get(ApiRoutes.TRANSACTIONS + "/" + transaction.id())
                        .header(HttpHeaders.AUTHORIZATION, actor.authorization()))
                .andExpect(status().isOk()).andReturn();
        assertThat(readBody(stored, TransactionResponse.class).amount()).isEqualByComparingTo(amount);
    }

    @Test
    void createsAndUpdatesBudgetLimitsSentAsJsonStrings() throws Exception {
        Actor actor = registerAndCreateCategory();
        Map<String, Object> request = Map.of("categoryId", actor.categoryId(), "period", PERIOD,
                "limitAmount", MAXIMUM_AMOUNT);
        BudgetResponse created = readBody(create(ApiRoutes.BUDGETS, actor, request), BudgetResponse.class);
        assertThat(created.limitAmount()).isEqualByComparingTo(MAXIMUM_AMOUNT);

        MvcResult result = mockMvc.perform(put(ApiRoutes.BUDGETS + "/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, actor.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("limitAmount", DECIMAL_AMOUNT))))
                .andExpect(status().isOk()).andReturn();
        BudgetResponse updated = readBody(result, BudgetResponse.class);
        assertThat(updated.limitAmount()).isEqualByComparingTo(DECIMAL_AMOUNT);
        assertThat(updated.categoryId()).isEqualTo(actor.categoryId());
        assertThat(updated.period()).isEqualTo(PERIOD);
    }

    private Actor registerAndCreateCategory() throws Exception {
        RegisterRequest registration = new RegisterRequest("frontend-contract@example.invalid", PASSWORD, "Frontend");
        MvcResult registered = mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated()).andReturn();
        AuthResponse auth = readBody(registered, AuthResponse.class);
        Actor owner = new Actor(AuthConstants.BEARER_PREFIX + auth.accessToken(), null);
        CategoryRequest request = new CategoryRequest("Frontend expense", CategoryType.CHI);
        CategoryResponse category = readBody(create(ApiRoutes.CATEGORIES, owner, request), CategoryResponse.class);
        Actor actor = new Actor(owner.authorization(), category.id());
        return actor;
    }

    private MvcResult create(String path, Actor actor, Object request) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, actor.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        return result;
    }

    private <T> T readBody(MvcResult result, Class<T> type) throws Exception {
        T response = jsonMapper.readValue(result.getResponse().getContentAsString(), type);
        return response;
    }

    record Actor(String authorization, Long categoryId) {
    }
}

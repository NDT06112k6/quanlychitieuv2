package com.example.expensetracker;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.AuthResponse;
import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.auth.LoginRequest;
import com.example.expensetracker.auth.RegisterRequest;
import com.example.expensetracker.budget.BudgetLimitRequest;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetRequest;
import com.example.expensetracker.budget.BudgetResponse;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.category.CategoryRenameRequest;
import com.example.expensetracker.category.CategoryRequest;
import com.example.expensetracker.category.CategoryResponse;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.transaction.DashboardResponse;
import com.example.expensetracker.transaction.TransactionRequest;
import com.example.expensetracker.transaction.TransactionResponse;
import com.example.expensetracker.user.PasswordChangeRequest;
import com.example.expensetracker.user.UserResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipIsolationIntegrationTests {

    private static final String PASSWORD = "integration-password";
    private static final String NEW_PASSWORD = "changed-integration-password";
    private static final String OWNER_EMAIL = "ownership-owner@example.invalid";
    private static final String OTHER_EMAIL = "ownership-other@example.invalid";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 8);
    private static final BigDecimal AMOUNT = new BigDecimal("12.34");
    private static final BigDecimal LIMIT = new BigDecimal("10.00");
    private static final BudgetPeriod PERIOD = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final JwtService jwtService;

    @Autowired
    OwnershipIsolationIntegrationTests(MockMvc mockMvc, JsonMapper jsonMapper, JwtService jwtService) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.jwtService = jwtService;
    }

    @Test
    void isolatesCategoriesTransactionsBudgetsAndDashboardAcrossTheEntireHttpFlow() throws Exception {
        TestActor owner = registerAndLogin(OWNER_EMAIL);
        OwnedResources resources = createResources(owner);
        assertDashboard(owner, AMOUNT);
        assertBudgetAlert(owner);
        assertDuplicateBudget(owner, resources.category());

        TestActor other = registerAndLogin(OTHER_EMAIL);
        assertEmptyListsWithForgedOwnerQuery(other, owner.userId());
        assertDashboard(other, BigDecimal.ZERO);
        assertForeignResourcesAreDenied(other, resources);
        assertForeignMutationsAreDenied(other, resources);
        assertForeignCategoryCannotBeBorrowed(other, resources.category());

        assertOriginalResourcesRemainIntact(owner, resources);
        updateAndDeleteOwnResources(owner, resources);
    }

    @Test
    void preventsForgedOwnerIdsFromChangingAnotherUsersProfileOrPassword() throws Exception {
        TestActor owner = registerAndLogin(OWNER_EMAIL);
        TestActor other = registerAndLogin(OTHER_EMAIL);
        Map<String, Object> profile = Map.of("displayName", "Other changed", "userId", owner.userId());
        MvcResult changed = mockMvc.perform(put(ApiRoutes.USER_ME).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(profile)))
                .andExpect(status().isOk()).andReturn();
        assertThat(readBody(changed, UserResponse.class).id()).isEqualTo(other.userId());
        mockMvc.perform(get(ApiRoutes.USER_ME).header(HttpHeaders.AUTHORIZATION, owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Owner"));

        PasswordChangeRequest password = new PasswordChangeRequest(PASSWORD, NEW_PASSWORD);
        mockMvc.perform(put(ApiRoutes.USER_PASSWORD).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(password)))
                .andExpect(status().isOk());
        login(OWNER_EMAIL, PASSWORD);
        login(OTHER_EMAIL, NEW_PASSWORD);
        mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(OTHER_EMAIL, PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns409WhenAnOwnedCategoryIsStillReferenced() throws Exception {
        TestActor owner = registerAndLogin(OWNER_EMAIL);
        OwnedResources resources = createResources(owner);

        mockMvc.perform(delete(item(ApiRoutes.CATEGORIES, resources.category().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.RESOURCE_IN_USE));
    }

    private TestActor registerAndLogin(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, PASSWORD, "Owner");
        mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request))).andExpect(status().isCreated());
        TestActor actor = login(email, PASSWORD);
        return actor;
    }

    private TestActor login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(AuthRoutes.LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn();
        AuthResponse auth = readBody(result, AuthResponse.class);
        Long userId = jwtService.readUserId(auth.accessToken());
        TestActor actor = new TestActor(AuthConstants.BEARER_PREFIX + auth.accessToken(), userId);
        return actor;
    }

    private OwnedResources createResources(TestActor actor) throws Exception {
        MvcResult categoryResult = create(ApiRoutes.CATEGORIES, actor, new CategoryRequest("Food", CategoryType.CHI));
        CategoryResponse category = readBody(categoryResult, CategoryResponse.class);
        TransactionRequest transactionRequest = new TransactionRequest(category.id(), AMOUNT, DATE, "Lunch");
        TransactionResponse transaction = readBody(create(ApiRoutes.TRANSACTIONS, actor, transactionRequest), TransactionResponse.class);
        BudgetRequest budgetRequest = new BudgetRequest(category.id(), PERIOD, LIMIT);
        BudgetResponse budget = readBody(create(ApiRoutes.BUDGETS, actor, budgetRequest), BudgetResponse.class);
        assertThat(categoryResult.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(item(ApiRoutes.CATEGORIES, category.id()));
        assertThat(transaction.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(transaction.createdAt()).isNotNull();
        OwnedResources resources = new OwnedResources(category, transaction, budget);
        return resources;
    }

    private MvcResult create(String path, TestActor actor, Object request) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, actor.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION)).andReturn();
        return result;
    }

    private void assertDashboard(TestActor actor, BigDecimal expectedExpense) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.TRANSACTIONS + ApiRoutes.DASHBOARD)
                        .header(HttpHeaders.AUTHORIZATION, actor.authorization())
                        .param("startDate", "2026-11-01").param("endDate", "2026-11-30"))
                .andExpect(status().isOk()).andReturn();
        DashboardResponse dashboard = readBody(result, DashboardResponse.class);
        assertThat(dashboard.totalExpense()).isEqualByComparingTo(expectedExpense);
        assertThat(dashboard.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dashboard.balance()).isEqualByComparingTo(expectedExpense.negate());
    }

    private void assertBudgetAlert(TestActor actor) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.BUDGETS + ApiRoutes.ALERTS)
                        .header(HttpHeaders.AUTHORIZATION, actor.authorization())
                        .param("type", PERIOD.type().name()).param("period", PERIOD.period()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("EXCEEDED")).andReturn();
        JsonNode alert = jsonMapper.readTree(result.getResponse().getContentAsString()).get("content").get(0);
        assertThat(alert.get("spent").decimalValue()).isEqualByComparingTo(AMOUNT);
        assertThat(alert.get("exceededAmount").decimalValue()).isEqualByComparingTo("2.34");
    }

    private void assertDuplicateBudget(TestActor actor, CategoryResponse category) throws Exception {
        BudgetRequest request = new BudgetRequest(category.id(), PERIOD, LIMIT);
        mockMvc.perform(post(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, actor.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(ErrorMessages.DUPLICATE_BUDGET));
    }

    private void assertEmptyListsWithForgedOwnerQuery(TestActor other, Long ownerId) throws Exception {
        for (String path : List.of(ApiRoutes.CATEGORIES, ApiRoutes.TRANSACTIONS, ApiRoutes.BUDGETS)) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, other.authorization()).param("userId", ownerId.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        }
        mockMvc.perform(get(ApiRoutes.BUDGETS + ApiRoutes.ALERTS).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .param("type", PERIOD.type().name()).param("period", PERIOD.period()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    private void assertForeignResourcesAreDenied(TestActor other, OwnedResources resources) throws Exception {
        List<String> paths = List.of(item(ApiRoutes.CATEGORIES, resources.category().id()),
                item(ApiRoutes.TRANSACTIONS, resources.transaction().id()), item(ApiRoutes.BUDGETS, resources.budget().id()));
        for (String path : paths) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, other.authorization())).andExpect(status().isForbidden());
            mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, other.authorization())).andExpect(status().isForbidden());
        }
    }

    private void assertForeignMutationsAreDenied(TestActor other, OwnedResources resources) throws Exception {
        mockMvc.perform(put(item(ApiRoutes.CATEGORIES, resources.category().id())).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(new CategoryRenameRequest("Forbidden"))))
                .andExpect(status().isForbidden());
        TransactionRequest transaction = new TransactionRequest(resources.category().id(), BigDecimal.ONE, DATE, "Forbidden");
        mockMvc.perform(put(item(ApiRoutes.TRANSACTIONS, resources.transaction().id())).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transaction)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(item(ApiRoutes.BUDGETS, resources.budget().id())).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(new BudgetLimitRequest(BigDecimal.ONE))))
                .andExpect(status().isForbidden());
    }

    private void assertForeignCategoryCannotBeBorrowed(TestActor other, CategoryResponse category) throws Exception {
        TransactionRequest transaction = new TransactionRequest(category.id(), AMOUNT, DATE, null);
        mockMvc.perform(post(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transaction)))
                .andExpect(status().isForbidden());
        BudgetRequest budget = new BudgetRequest(category.id(), PERIOD, LIMIT);
        mockMvc.perform(post(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(budget)))
                .andExpect(status().isForbidden());
    }

    private void assertOriginalResourcesRemainIntact(TestActor owner, OwnedResources resources) throws Exception {
        mockMvc.perform(get(item(ApiRoutes.CATEGORIES, resources.category().id())).header(HttpHeaders.AUTHORIZATION, owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Food"));
        MvcResult transaction = mockMvc.perform(get(item(ApiRoutes.TRANSACTIONS, resources.transaction().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization())).andExpect(status().isOk()).andReturn();
        assertThat(readBody(transaction, TransactionResponse.class).amount()).isEqualByComparingTo(AMOUNT);
        MvcResult budget = mockMvc.perform(get(item(ApiRoutes.BUDGETS, resources.budget().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization())).andExpect(status().isOk()).andReturn();
        assertThat(readBody(budget, BudgetResponse.class).limitAmount()).isEqualByComparingTo(LIMIT);
        assertDashboard(owner, AMOUNT);
    }

    private void updateAndDeleteOwnResources(TestActor owner, OwnedResources resources) throws Exception {
        mockMvc.perform(put(item(ApiRoutes.CATEGORIES, resources.category().id())).header(HttpHeaders.AUTHORIZATION, owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(new CategoryRenameRequest("Groceries"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value(CategoryType.CHI.name()));
        TransactionRequest transaction = new TransactionRequest(resources.category().id(), new BigDecimal("7.50"), DATE, "Updated");
        mockMvc.perform(put(item(ApiRoutes.TRANSACTIONS, resources.transaction().id())).header(HttpHeaders.AUTHORIZATION, owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transaction))).andExpect(status().isOk());
        Map<String, Object> limit = Map.of("limitAmount", new BigDecimal("20.00"), "categoryId", 999L, "type", "WEEKLY");
        MvcResult updated = mockMvc.perform(put(item(ApiRoutes.BUDGETS, resources.budget().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(limit))).andExpect(status().isOk()).andReturn();
        BudgetResponse budget = readBody(updated, BudgetResponse.class);
        assertThat(budget.categoryId()).isEqualTo(resources.category().id());
        assertThat(budget.period()).isEqualTo(PERIOD);
        assertThat(budget.limitAmount()).isEqualByComparingTo("20.00");
        assertDashboard(owner, new BigDecimal("7.50"));
        mockMvc.perform(delete(item(ApiRoutes.TRANSACTIONS, resources.transaction().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization())).andExpect(status().isNoContent());
        mockMvc.perform(delete(item(ApiRoutes.BUDGETS, resources.budget().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization())).andExpect(status().isNoContent());
        mockMvc.perform(delete(item(ApiRoutes.CATEGORIES, resources.category().id()))
                        .header(HttpHeaders.AUTHORIZATION, owner.authorization())).andExpect(status().isNoContent());
    }

    private <T> T readBody(MvcResult result, Class<T> type) throws Exception {
        T body = jsonMapper.readValue(result.getResponse().getContentAsString(), type);
        return body;
    }

    private String item(String path, Long id) {
        String resourcePath = path + "/" + id;
        return resourcePath;
    }

    record TestActor(String authorization, Long userId) {
    }

    record OwnedResources(CategoryResponse category, TransactionResponse transaction, BudgetResponse budget) {
    }
}

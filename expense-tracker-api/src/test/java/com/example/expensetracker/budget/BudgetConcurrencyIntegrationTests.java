package com.example.expensetracker.budget;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.AuthResponse;
import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.RegisterRequest;
import com.example.expensetracker.category.CategoryRequest;
import com.example.expensetracker.category.CategoryResponse;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.exception.ApiErrorResponse;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.common.web.ApiRoutes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Worker requests need committed fixtures, rather than a transaction bound to the test thread.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:budget_concurrency;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetConcurrencyIntegrationTests {

    private static final int REQUEST_COUNT = 2;
    private static final long WAIT_SECONDS = 20;
    private static final String EMAIL = "concurrent-budget@example.invalid";
    private static final String PASSWORD = "concurrent-test-password";
    private static final BudgetPeriod PERIOD = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private static final BigDecimal LIMIT = new BigDecimal("100.00");
    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    @Autowired
    BudgetConcurrencyIntegrationTests(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    @Test
    void concurrentCreatesReturnOne201AndOne409WithoutOverwritingTheBudget() throws Exception {
        CreationFixture fixture = createFixture();

        List<MvcResult> responses = createConcurrently(fixture);

        List<Integer> responseStatuses = new ArrayList<>();
        for (MvcResult response : responses) {
            int responseStatus = response.getResponse().getStatus();
            responseStatuses.add(responseStatus);
            if (responseStatus == HttpStatus.CONFLICT.value()) {
                assertUniformConflict(response);
            }
        }
        assertThat(responseStatuses).containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
        MvcResult stored = mockMvc.perform(get(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, fixture.authorization())
                        .param("categoryId", fixture.categoryId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].period.period").value(PERIOD.period()))
                .andReturn();
        BigDecimal savedLimit = jsonMapper.readTree(stored.getResponse().getContentAsString())
                .get("content").get(0).get("limitAmount").decimalValue();
        assertThat(savedLimit).isEqualByComparingTo(LIMIT);
    }

    private CreationFixture createFixture() throws Exception {
        RegisterRequest registration = new RegisterRequest(EMAIL, PASSWORD, "Concurrent owner");
        MvcResult registered = mockMvc.perform(post(AuthRoutes.REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated()).andReturn();
        AuthResponse auth = jsonMapper.readValue(registered.getResponse().getContentAsString(), AuthResponse.class);
        String authorization = AuthConstants.BEARER_PREFIX + auth.accessToken();
        CategoryRequest categoryRequest = new CategoryRequest("Concurrent expense", CategoryType.CHI);
        MvcResult created = mockMvc.perform(post(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(categoryRequest)))
                .andExpect(status().isCreated()).andReturn();
        CategoryResponse category = jsonMapper.readValue(created.getResponse().getContentAsString(), CategoryResponse.class);
        BudgetRequest budget = new BudgetRequest(category.id(), PERIOD, LIMIT);
        CreationFixture fixture = new CreationFixture(authorization, category.id(), jsonMapper.writeValueAsString(budget));
        return fixture;
    }

    private List<MvcResult> createConcurrently(CreationFixture fixture) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            Future<MvcResult> first = workers.submit(() -> createAfterSignal(fixture, ready, start));
            Future<MvcResult> second = workers.submit(() -> createAfterSignal(fixture, ready, start));
            try {
                assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<MvcResult> results = List.of(first.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    second.get(WAIT_SECONDS, TimeUnit.SECONDS));
            return results;
        }
    }

    private MvcResult createAfterSignal(CreationFixture fixture, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        MvcResult result = mockMvc.perform(post(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, fixture.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(fixture.body()))
                .andReturn();
        return result;
    }

    private void assertUniformConflict(MvcResult result) throws Exception {
        ApiErrorResponse error = jsonMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(error.message()).isEqualTo(ErrorMessages.DUPLICATE_BUDGET);
        assertThat(error.path()).isEqualTo(ApiRoutes.BUDGETS);
        assertThat(error.timestamp()).isNotNull();
    }

    record CreationFixture(String authorization, Long categoryId, String body) {
    }
}

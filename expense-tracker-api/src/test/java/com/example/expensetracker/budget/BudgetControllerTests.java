package com.example.expensetracker.budget;

import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.support.ControllerTestSupport;
import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.support.RestControllerTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.expensetracker.support.DomainFixtures.AMOUNT;
import static com.example.expensetracker.support.DomainFixtures.CATEGORY_ID;
import static com.example.expensetracker.support.DomainFixtures.RESOURCE_ID;
import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RestControllerTest(BudgetController.class)
class BudgetControllerTests extends ControllerTestSupport {

    private static final BudgetPeriod PERIOD = new BudgetPeriod(BudgetType.MONTHLY, "2026-11");
    private static final BudgetRequest REQUEST = new BudgetRequest(CATEGORY_ID, PERIOD, AMOUNT);
    private static final BudgetResponse BUDGET = BudgetResponse.from(DomainFixtures.budget(PERIOD));
    private static final String ITEM = ApiRoutes.BUDGETS + "/" + RESOURCE_ID;
    private static final String BODY = """
            {"categoryId":10,"period":{"type":"MONTHLY","period":"2026-11"},"limitAmount":12.34,"userId":999}
            """;
    @MockitoBean
    private BudgetService budgetService;

    @Autowired
    BudgetControllerTests(MockMvc mockMvc, JwtService jwtService) {
        super(mockMvc, jwtService);
    }

    @Test
    void createsBudgetWithLocationAndTheTokenOwner() throws Exception {
        when(budgetService.createBudget(USER_ID, REQUEST)).thenReturn(BUDGET);

        mockMvc.perform(post(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, ITEM));
        verify(budgetService).createBudget(USER_ID, REQUEST);
    }

    @Test
    void readsAndUpdatesOnlyTheLimitWithTheTokenOwner() throws Exception {
        BudgetLimitRequest request = new BudgetLimitRequest(AMOUNT);
        when(budgetService.getBudget(USER_ID, RESOURCE_ID)).thenReturn(BUDGET);
        when(budgetService.updateBudgetLimit(USER_ID, RESOURCE_ID, request)).thenReturn(BUDGET);

        mockMvc.perform(get(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.period.period").value(PERIOD.period()));
        mockMvc.perform(put(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":12.34,\"categoryId\":999,\"userId\":999}"))
                .andExpect(status().isOk());
        verify(budgetService).getBudget(USER_ID, RESOURCE_ID);
        verify(budgetService).updateBudgetLimit(USER_ID, RESOURCE_ID, request);
    }

    @Test
    void deletesWith204() throws Exception {
        mockMvc.perform(delete(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())).andExpect(status().isNoContent());
        verify(budgetService).deleteBudget(USER_ID, RESOURCE_ID);
    }

    @Test
    void filtersAndCapsBudgetPages() throws Exception {
        BudgetFilter filter = new BudgetFilter(CATEGORY_ID, PERIOD.type(), PERIOD.period());
        when(budgetService.getBudgets(eq(USER_ID), eq(filter), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("categoryId", CATEGORY_ID.toString()).param("type", "MONTHLY").param("period", "2026-11")
                        .param("size", "1000").param("userId", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(budgetService).getBudgets(eq(USER_ID), eq(filter), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void servesPagedAlertsForTheSelectedPeriod() throws Exception {
        when(budgetService.getBudgetAlerts(eq(USER_ID), eq(PERIOD), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.BUDGETS + ApiRoutes.ALERTS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("type", "MONTHLY").param("period", "2026-11").param("size", "1000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(budgetService).getBudgetAlerts(eq(USER_ID), eq(PERIOD), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void returns409ForDuplicateCreation() throws Exception {
        when(budgetService.createBudget(USER_ID, REQUEST)).thenThrow(new DuplicateBudgetException());

        mockMvc.perform(post(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(ErrorMessages.DUPLICATE_BUDGET));
    }

    @Test
    void rejectsInvalidLimitPeriodAndMissingAlertParameters() throws Exception {
        mockMvc.perform(put(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":0.001}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("type", "MONTHLY").param("period", "2026-13"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.BUDGETS + ApiRoutes.ALERTS).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(budgetService);
    }

    @Test
    void rejectsUnapprovedSortAndMissingToken() throws Exception {
        mockMvc.perform(get(ApiRoutes.BUDGETS).header(HttpHeaders.AUTHORIZATION, authorization()).param("sort", "limitAmount,desc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.BUDGETS)).andExpect(status().isUnauthorized());
        verifyNoInteractions(budgetService);
    }

    @Test
    void returns403WhenUpdatingAnotherOwnersBudget() throws Exception {
        when(budgetService.updateBudgetLimit(USER_ID, RESOURCE_ID, new BudgetLimitRequest(AMOUNT)))
                .thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(put(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":12.34}"))
                .andExpect(status().isForbidden());
    }
}

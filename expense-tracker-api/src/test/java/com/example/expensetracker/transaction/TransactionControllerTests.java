package com.example.expensetracker.transaction;

import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.DateRange;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.support.ControllerTestSupport;
import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.support.RestControllerTest;
import java.time.LocalDate;
import java.math.BigDecimal;
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

import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static com.example.expensetracker.support.DomainFixtures.CATEGORY_ID;
import static com.example.expensetracker.support.DomainFixtures.RESOURCE_ID;
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

@RestControllerTest(TransactionController.class)
class TransactionControllerTests extends ControllerTestSupport {

    private static final String ITEM = ApiRoutes.TRANSACTIONS + "/" + RESOURCE_ID;
    private static final String BODY = """
            {"categoryId":10,"amount":12.34,"occurredOn":"2026-11-01","note":"Lunch","userId":999}
            """;
    private static final TransactionResponse TRANSACTION = TransactionResponse.from(DomainFixtures.transaction());
    private static final DateRange RANGE = new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    TransactionControllerTests(MockMvc mockMvc, JwtService jwtService) {
        super(mockMvc, jwtService);
    }

    @Test
    void createsWithLocationUsingTheTokenOwnerDespiteForgedBodyId() throws Exception {
        when(transactionService.createTransaction(USER_ID, DomainFixtures.transactionRequest())).thenReturn(TRANSACTION);

        mockMvc.perform(post(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, ITEM));

        verify(transactionService).createTransaction(USER_ID, DomainFixtures.transactionRequest());
    }

    @Test
    void readsAndUpdatesWithTheTokenOwner() throws Exception {
        when(transactionService.getTransaction(USER_ID, RESOURCE_ID)).thenReturn(TRANSACTION);
        when(transactionService.updateTransaction(USER_ID, RESOURCE_ID, DomainFixtures.transactionRequest())).thenReturn(TRANSACTION);

        mockMvc.perform(get(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(12.34));
        mockMvc.perform(put(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());

        verify(transactionService).getTransaction(USER_ID, RESOURCE_ID);
        verify(transactionService).updateTransaction(USER_ID, RESOURCE_ID, DomainFixtures.transactionRequest());
    }

    @Test
    void deletesWith204ForTheTokenOwner() throws Exception {
        mockMvc.perform(delete(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())).andExpect(status().isNoContent());
        verify(transactionService).deleteTransaction(USER_ID, RESOURCE_ID);
    }

    @Test
    void mapsAllFiltersAndCapsTheRequestedPage() throws Exception {
        TransactionFilter filter = new TransactionFilter(CATEGORY_ID, CategoryType.CHI, RANGE);
        when(transactionService.getTransactions(eq(USER_ID), eq(filter), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("startDate", "2026-11-01").param("endDate", "2026-11-30")
                        .param("categoryId", CATEGORY_ID.toString()).param("type", "CHI")
                        .param("size", "1000").param("page", "1").param("userId", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).getTransactions(eq(USER_ID), eq(filter), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void usesAllSupportedDatesWhenFiltersAreOmitted() throws Exception {
        when(transactionService.getTransactions(eq(USER_ID), eq(TransactionFilter.all()), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization())).andExpect(status().isOk());
        verify(transactionService).getTransactions(eq(USER_ID), eq(TransactionFilter.all()), any());
    }

    @Test
    void servesDashboardUsingThePrincipalAndDateRange() throws Exception {
        DashboardResponse dashboard = new DashboardResponse(new BigDecimal("20.00"), new BigDecimal("12.34"), new BigDecimal("7.66"));
        when(transactionService.getDashboard(USER_ID, RANGE)).thenReturn(dashboard);

        mockMvc.perform(get(ApiRoutes.TRANSACTIONS + ApiRoutes.DASHBOARD).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("startDate", "2026-11-01").param("endDate", "2026-11-30").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(7.66));
        verify(transactionService).getDashboard(USER_ID, RANGE);
    }

    @Test
    void rejectsInvalidBodyDateRangeAndTypedParameters() throws Exception {
        mockMvc.perform(post(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("12.34", "0.001")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("startDate", "2026-12-01").param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization()).param("startDate", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization()).param("categoryId", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsUnapprovedSortAndMissingToken() throws Exception {
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS).header(HttpHeaders.AUTHORIZATION, authorization()).param("sort", "note,desc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.TRANSACTIONS)).andExpect(status().isUnauthorized());
        verifyNoInteractions(transactionService);
    }

    @Test
    void mapsOwnershipDenialTo403() throws Exception {
        when(transactionService.getTransaction(USER_ID, RESOURCE_ID)).thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(get(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())).andExpect(status().isForbidden());
    }
}

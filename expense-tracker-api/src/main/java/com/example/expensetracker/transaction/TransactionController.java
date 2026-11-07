package com.example.expensetracker.transaction;

import com.example.expensetracker.auth.AuthPrincipal;
import com.example.expensetracker.common.util.PageableUtil;
import com.example.expensetracker.common.web.ApiDocumentation;
import com.example.expensetracker.common.web.ApiErrorResponses;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.common.web.DateRangeParameters;
import com.example.expensetracker.common.web.RestResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.TRANSACTIONS)
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Owner-scoped transactions and Dashboard totals")
@SecurityRequirement(name = ApiDocumentation.BEARER_SCHEME)
@ApiErrorResponses
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a transaction")
    @ApiResponse(responseCode = "201", description = "Transaction created with its Location")
    public ResponseEntity<TransactionResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse transaction = transactionService.createTransaction(principal.userId(), request);
        ResponseEntity<TransactionResponse> response = RestResponses.created(ApiRoutes.TRANSACTIONS, transaction.id(), transaction);
        return response;
    }

    @GetMapping
    @Operation(summary = "List my transactions", description = ApiDocumentation.PAGINATION)
    @ApiResponse(responseCode = "200", description = "Page of transactions matching inclusive date/category/type filters")
    public Page<TransactionResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute @ParameterObject TransactionQueryRequest query, @ParameterObject Pageable pageable) {
        Pageable bounded = PageableUtil.bounded(pageable, PageableUtil.TRANSACTION_SORT_FIELDS);
        TransactionFilter filter = query.toFilter();
        Page<TransactionResponse> response = transactionService.getTransactions(principal.userId(), filter, bounded);
        return response;
    }

    @GetMapping(ApiRoutes.DASHBOARD)
    @Operation(summary = "Get my Dashboard", description = "Inclusive date range; omitted bounds include all supported dates.")
    @ApiResponse(responseCode = "200", description = "Income, expense and balance totals")
    public DashboardResponse dashboard(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute @ParameterObject DateRangeParameters query) {
        DashboardResponse response = transactionService.getDashboard(principal.userId(), query.toRange());
        return response;
    }

    @GetMapping(ApiRoutes.ITEM)
    @Operation(summary = "Get my transaction")
    @ApiResponse(responseCode = "200", description = "Transaction details")
    public TransactionResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long transactionId) {
        TransactionResponse response = transactionService.getTransaction(principal.userId(), transactionId);
        return response;
    }

    @PutMapping(ApiRoutes.ITEM)
    @Operation(summary = "Update my transaction")
    @ApiResponse(responseCode = "200", description = "Updated transaction")
    public TransactionResponse update(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long transactionId, @Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.updateTransaction(principal.userId(), transactionId, request);
        return response;
    }

    @DeleteMapping(ApiRoutes.ITEM)
    @Operation(summary = "Delete my transaction")
    @ApiResponse(responseCode = "204", description = "Transaction deleted")
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long transactionId) {
        transactionService.deleteTransaction(principal.userId(), transactionId);
        ResponseEntity<Void> response = ResponseEntity.noContent().build();
        return response;
    }
}

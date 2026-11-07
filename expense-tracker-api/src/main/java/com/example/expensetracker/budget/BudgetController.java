package com.example.expensetracker.budget;

import com.example.expensetracker.auth.AuthPrincipal;
import com.example.expensetracker.common.exception.ApiErrorResponse;
import com.example.expensetracker.common.util.PageableUtil;
import com.example.expensetracker.common.web.ApiDocumentation;
import com.example.expensetracker.common.web.ApiErrorResponses;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.common.web.RestResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping(ApiRoutes.BUDGETS)
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Expense budgets and spending alerts for the authenticated user")
@SecurityRequirement(name = ApiDocumentation.BEARER_SCHEME)
@ApiErrorResponses
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    @Operation(summary = "Create a budget", description = "Creation never overwrites an existing budget.")
    @ApiResponse(responseCode = "201", description = "Budget created with its Location")
    @ApiResponse(responseCode = "409", description = "Budget already exists for this category/period/type",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<BudgetResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BudgetRequest request) {
        BudgetResponse budget = budgetService.createBudget(principal.userId(), request);
        ResponseEntity<BudgetResponse> response = RestResponses.created(ApiRoutes.BUDGETS, budget.id(), budget);
        return response;
    }

    @GetMapping
    @Operation(summary = "List my budgets", description = ApiDocumentation.PAGINATION)
    @ApiResponse(responseCode = "200", description = "Page of budgets matching category/period/type filters")
    public Page<BudgetResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute @ParameterObject BudgetQueryRequest query, @ParameterObject Pageable pageable) {
        Pageable bounded = PageableUtil.forController(pageable);
        BudgetFilter filter = query.toFilter();
        Page<BudgetResponse> response = budgetService.getBudgets(principal.userId(), filter, bounded);
        return response;
    }

    @GetMapping(ApiRoutes.ALERTS)
    @Operation(summary = "List my budget alerts", description = ApiDocumentation.PAGINATION)
    @ApiResponse(responseCode = "200", description = "Page of alerts for the required period and type")
    public Page<BudgetAlert> alerts(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute @ParameterObject BudgetAlertQueryRequest query, @ParameterObject Pageable pageable) {
        Pageable bounded = PageableUtil.forController(pageable);
        BudgetPeriod period = query.toPeriod();
        Page<BudgetAlert> response = budgetService.getBudgetAlerts(principal.userId(), period, bounded);
        return response;
    }

    @GetMapping(ApiRoutes.ITEM)
    @Operation(summary = "Get my budget")
    @ApiResponse(responseCode = "200", description = "Budget details")
    public BudgetResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long budgetId) {
        BudgetResponse response = budgetService.getBudget(principal.userId(), budgetId);
        return response;
    }

    @PutMapping(ApiRoutes.ITEM)
    @Operation(summary = "Change my budget limit", description = "Category, period and type are immutable.")
    @ApiResponse(responseCode = "200", description = "Updated budget")
    public BudgetResponse updateLimit(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long budgetId, @Valid @RequestBody BudgetLimitRequest request) {
        BudgetResponse response = budgetService.updateBudgetLimit(principal.userId(), budgetId, request);
        return response;
    }

    @DeleteMapping(ApiRoutes.ITEM)
    @Operation(summary = "Delete my budget")
    @ApiResponse(responseCode = "204", description = "Budget deleted")
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long budgetId) {
        budgetService.deleteBudget(principal.userId(), budgetId);
        ResponseEntity<Void> response = ResponseEntity.noContent().build();
        return response;
    }
}

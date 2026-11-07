package com.example.expensetracker.category;

import com.example.expensetracker.auth.AuthPrincipal;
import com.example.expensetracker.common.util.PageableUtil;
import com.example.expensetracker.common.web.ApiDocumentation;
import com.example.expensetracker.common.web.ApiErrorResponses;
import com.example.expensetracker.common.web.ApiRoutes;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.CATEGORIES)
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Owner-scoped income and expense categories")
@SecurityRequirement(name = ApiDocumentation.BEARER_SCHEME)
@ApiErrorResponses
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Category created with its Location")
    public ResponseEntity<CategoryResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.createCategory(principal.userId(), request);
        ResponseEntity<CategoryResponse> response = RestResponses.created(ApiRoutes.CATEGORIES, category.id(), category);
        return response;
    }

    @GetMapping
    @Operation(summary = "List my categories", description = ApiDocumentation.PAGINATION)
    @ApiResponse(responseCode = "200", description = "Page of categories")
    public Page<CategoryResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) CategoryType type, @ParameterObject Pageable pageable) {
        Pageable bounded = PageableUtil.forController(pageable);
        Page<CategoryResponse> response;
        if (type == null) {
            response = categoryService.getCategories(principal.userId(), bounded);
        } else {
            response = categoryService.getCategoriesByType(principal.userId(), type, bounded);
        }
        return response;
    }

    @GetMapping(ApiRoutes.ITEM)
    @Operation(summary = "Get my category")
    @ApiResponse(responseCode = "200", description = "Category details")
    public CategoryResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long categoryId) {
        CategoryResponse response = categoryService.getCategory(principal.userId(), categoryId);
        return response;
    }

    @PutMapping(ApiRoutes.ITEM)
    @Operation(summary = "Rename my category", description = "Category type is immutable.")
    @ApiResponse(responseCode = "200", description = "Renamed category")
    public CategoryResponse rename(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long categoryId, @Valid @RequestBody CategoryRenameRequest request) {
        CategoryResponse response = categoryService.renameCategory(principal.userId(), categoryId, request.name());
        return response;
    }

    @DeleteMapping(ApiRoutes.ITEM)
    @Operation(summary = "Delete my category")
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(responseCode = "409", description = "Category is referenced by transactions or budgets")
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable(ApiRoutes.ID) @Positive Long categoryId) {
        categoryService.deleteCategory(principal.userId(), categoryId);
        ResponseEntity<Void> response = ResponseEntity.noContent().build();
        return response;
    }
}

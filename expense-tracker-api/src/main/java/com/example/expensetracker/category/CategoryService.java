package com.example.expensetracker.category;

import com.example.expensetracker.common.util.AccessGuard;
import com.example.expensetracker.common.util.DomainValidation;
import com.example.expensetracker.common.util.PageableUtil;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;

    @Transactional
    public CategoryResponse createCategory(@NotNull @Positive Long userId, @Valid @NotNull CategoryRequest request) {
        User user = userService.requireUser(userId);
        Category category = new Category(user, request.name(), request.type());
        Category saved = categoryRepository.save(category);
        CategoryResponse response = CategoryResponse.from(saved);
        return response;
    }

    public Category requireOwnedCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId) {
        Category category = AccessGuard.requireOwned(categoryRepository.findByIdAndUserId(categoryId, userId));
        return category;
    }

    public CategoryResponse getCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId) {
        Category category = requireOwnedCategory(userId, categoryId);
        CategoryResponse response = CategoryResponse.from(category);
        return response;
    }

    public Page<CategoryResponse> getCategories(@NotNull @Positive Long userId, @NotNull Pageable pageable) {
        Pageable boundedPageable = PageableUtil.bounded(pageable, PageableUtil.CATEGORY_SORT_FIELDS);
        Page<Category> categories = categoryRepository.findAllByUserId(userId, boundedPageable);
        Page<CategoryResponse> responses = categories.map(CategoryResponse::from);
        return responses;
    }

    public Page<CategoryResponse> getCategoriesByType(@NotNull @Positive Long userId,
            @NotNull CategoryType type, @NotNull Pageable pageable) {
        Pageable boundedPageable = PageableUtil.bounded(pageable, PageableUtil.CATEGORY_SORT_FIELDS);
        Page<Category> categories = categoryRepository.findAllByUserIdAndType(userId, type, boundedPageable);
        Page<CategoryResponse> responses = categories.map(CategoryResponse::from);
        return responses;
    }

    @Transactional
    public CategoryResponse renameCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId,
            @NotBlank @Size(max = Category.MAXIMUM_NAME_LENGTH) String name) {
        String validName = DomainValidation.requireText(name, Category.MAXIMUM_NAME_LENGTH);
        int affectedRows = categoryRepository.renameOwned(userId, categoryId, validName);
        AccessGuard.requireSuccessfulWrite(affectedRows);
        CategoryResponse response = getCategory(userId, categoryId);
        return response;
    }

    @Transactional
    public void deleteCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId) {
        int affectedRows = categoryRepository.deleteOwned(userId, categoryId);
        AccessGuard.requireSuccessfulWrite(affectedRows);
    }

    @Transactional
    public Category requireExpenseCategoryForUpdate(@NotNull @Positive Long userId,
            @NotNull @Positive Long categoryId) {
        Category category = AccessGuard.requireOwned(categoryRepository.findOwnedForUpdate(categoryId, userId));
        if (category.getType() != CategoryType.CHI) {
            throw new IllegalArgumentException("Budgets require an expense category");
        }
        return category;
    }
}

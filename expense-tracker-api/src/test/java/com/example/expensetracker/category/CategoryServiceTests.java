package com.example.expensetracker.category;

import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.user.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import static com.example.expensetracker.support.DomainFixtures.CATEGORY_ID;
import static com.example.expensetracker.support.DomainFixtures.OTHER_USER_ID;
import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createsCategoryForTheAuthenticatedOwner() {
        when(userService.requireUser(USER_ID)).thenReturn(DomainFixtures.user());
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(USER_ID, new CategoryRequest("Food", CategoryType.CHI));

        assertThat(response.name()).isEqualTo("Food");
        verify(userService).requireUser(USER_ID);
    }

    @Test
    void deniesReadingAnotherUsersCategory() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(OTHER_USER_ID, CATEGORY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesRenamingAnotherUsersCategory() {
        when(categoryRepository.renameOwned(OTHER_USER_ID, CATEGORY_ID, "Changed")).thenReturn(0);

        assertThatThrownBy(() -> categoryService.renameCategory(OTHER_USER_ID, CATEGORY_ID, "Changed"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesDeletingAnotherUsersCategory() {
        when(categoryRepository.deleteOwned(OTHER_USER_ID, CATEGORY_ID)).thenReturn(0);

        assertThatThrownBy(() -> categoryService.deleteCategory(OTHER_USER_ID, CATEGORY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void returnsOnlyTheRequestedOwnersPage() {
        when(categoryRepository.findAllByUserId(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(DomainFixtures.category())));

        Page<CategoryResponse> categories = categoryService.getCategories(USER_ID, PageRequest.of(0, 10));

        assertThat(categories.getContent()).extracting(CategoryResponse::name).containsExactly("Food");
    }

    @Test
    void rejectsIncomeCategoryForBudgets() {
        Category incomeCategory = new Category(DomainFixtures.user(), "Salary", CategoryType.THU);
        when(categoryRepository.findOwnedForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> categoryService.requireExpenseCategoryForUpdate(USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

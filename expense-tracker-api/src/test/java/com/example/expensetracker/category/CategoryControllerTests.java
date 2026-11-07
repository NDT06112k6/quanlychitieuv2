package com.example.expensetracker.category;

import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.support.ControllerTestSupport;
import com.example.expensetracker.support.RestControllerTest;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.expensetracker.support.DomainFixtures.CATEGORY_ID;
import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RestControllerTest(CategoryController.class)
class CategoryControllerTests extends ControllerTestSupport {

    private static final CategoryResponse CATEGORY = new CategoryResponse(CATEGORY_ID, "Food", CategoryType.CHI);
    private static final String ITEM = ApiRoutes.CATEGORIES + "/" + CATEGORY_ID;
    @MockitoBean
    private CategoryService categoryService;

    @Autowired
    CategoryControllerTests(MockMvc mockMvc, JwtService jwtService) {
        super(mockMvc, jwtService);
    }

    @Test
    void createsWithLocationUsingOnlyTheTokenOwner() throws Exception {
        CategoryRequest request = new CategoryRequest("Food", CategoryType.CHI);
        when(categoryService.createCategory(USER_ID, request)).thenReturn(CATEGORY);

        mockMvc.perform(post(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Food\",\"type\":\"CHI\",\"userId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, ITEM))
                .andExpect(jsonPath("$.name").value("Food"));

        verify(categoryService).createCategory(USER_ID, request);
    }

    @Test
    void getsOwnedCategory() throws Exception {
        when(categoryService.getCategory(USER_ID, CATEGORY_ID)).thenReturn(CATEGORY);

        mockMvc.perform(get(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(CATEGORY_ID));
        verify(categoryService).getCategory(USER_ID, CATEGORY_ID);
    }

    @Test
    void renamesWithoutAcceptingATypeChange() throws Exception {
        when(categoryService.renameCategory(USER_ID, CATEGORY_ID, "Groceries")).thenReturn(CATEGORY);

        mockMvc.perform(put(ITEM).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Groceries\"}"))
                .andExpect(status().isOk());
        verify(categoryService).renameCategory(USER_ID, CATEGORY_ID, "Groceries");
    }

    @Test
    void deletesOwnedCategoryWith204() throws Exception {
        mockMvc.perform(delete(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(categoryService).deleteCategory(USER_ID, CATEGORY_ID);
    }

    @Test
    void capsTheRequestedPageAndIgnoresForgedOwnerQuery() throws Exception {
        when(categoryService.getCategories(eq(USER_ID), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization())
                        .param("page", "2").param("size", "1000").param("userId", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryService).getCategories(eq(USER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void filtersCategoriesByType() throws Exception {
        when(categoryService.getCategoriesByType(eq(USER_ID), eq(CategoryType.CHI), any())).thenReturn(Page.empty());

        mockMvc.perform(get(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization()).param("type", "CHI"))
                .andExpect(status().isOk());
        verify(categoryService).getCategoriesByType(eq(USER_ID), eq(CategoryType.CHI), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown,desc", "user.passwordHash,desc", "amount,desc"})
    void rejectsUnsupportedSortBeforeCallingService(String sort) throws Exception {
        mockMvc.perform(get(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization()).param("sort", sort))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(categoryService);
    }

    @Test
    void rejectsInvalidBodyAndInvalidPathType() throws Exception {
        mockMvc.perform(post(ApiRoutes.CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Food\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.CATEGORIES + "/not-a-number").header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(categoryService);
    }

    @Test
    void returns403ForOwnershipDenial() throws Exception {
        when(categoryService.getCategory(USER_ID, CATEGORY_ID)).thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(get(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(ErrorMessages.ACCESS_DENIED));
    }

    @Test
    void returns409WhenCategoryIsReferenced() throws Exception {
        org.hibernate.exception.ConstraintViolationException conflict = new org.hibernate.exception.ConstraintViolationException(
                "Referenced", new SQLException("Referenced", "23000", 1451), "fk_transactions_category");
        doThrow(new DataIntegrityViolationException("Referenced", conflict)).when(categoryService).deleteCategory(USER_ID, CATEGORY_ID);

        mockMvc.perform(delete(ITEM).header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(ErrorMessages.RESOURCE_IN_USE));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get(ApiRoutes.CATEGORIES)).andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }
}

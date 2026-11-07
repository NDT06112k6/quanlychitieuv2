package com.example.expensetracker.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableUtilTests {

    @Test
    void capsSizeAndDefaultsToDescendingId() {
        Pageable result = PageableUtil.bounded(PageRequest.of(2, 1000), PageableUtil.TRANSACTION_SORT_FIELDS);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(PageableUtil.MAX_PAGE_SIZE);
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    void preservesMultipleSortsAndAddsStableIdTieBreaker() {
        Sort sort = Sort.by("amount").and(Sort.by(Sort.Direction.DESC, "occurredOn"));
        Pageable result = PageableUtil.bounded(PageRequest.of(0, 10, sort), PageableUtil.TRANSACTION_SORT_FIELDS);

        assertThat(result.getSort()).isEqualTo(sort.and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void preservesExplicitAscendingIdWithoutAddingAConflictingOrder() {
        Sort sort = Sort.by("id");
        Pageable result = PageableUtil.bounded(PageRequest.of(0, 10, sort), PageableUtil.CATEGORY_SORT_FIELDS);

        assertThat(result.getSort()).isEqualTo(sort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "user.id", "category.name", "name", "amount desc"})
    void rejectsFieldsOutsideTheResourceWhitelist(String field) {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(field));

        assertThatThrownBy(() -> PageableUtil.bounded(pageable, PageableUtil.TRANSACTION_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedIgnoreCaseOption() {
        Sort sort = Sort.by(Sort.Order.asc("name").ignoreCase());
        Pageable pageable = PageRequest.of(0, 10, sort);

        assertThatThrownBy(() -> PageableUtil.bounded(pageable, PageableUtil.CATEGORY_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPagination() {
        assertThatThrownBy(() -> PageableUtil.bounded(null, PageableUtil.BUDGET_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

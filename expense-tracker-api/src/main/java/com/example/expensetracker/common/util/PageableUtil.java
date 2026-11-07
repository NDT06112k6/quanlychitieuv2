package com.example.expensetracker.common.util;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableUtil {

    public static final int MAX_PAGE_SIZE = 100;
    private static final String ID_PROPERTY = "id";
    public static final Set<String> CATEGORY_SORT_FIELDS = Set.of(ID_PROPERTY, "name");
    public static final Set<String> TRANSACTION_SORT_FIELDS = Set.of(ID_PROPERTY, "occurredOn", "amount");
    public static final Set<String> BUDGET_SORT_FIELDS = Set.of(ID_PROPERTY, "limitAmount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, ID_PROPERTY);

    private PageableUtil() {
    }

    public static Pageable bounded(Pageable pageable, Set<String> allowedFields) {
        if (pageable == null || pageable.isUnpaged()) {
            throw new IllegalArgumentException("Pagination is required");
        }
        Sort sort = validatedSort(pageable.getSort(), allowedFields);
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);
        return boundedPageable;
    }

    private static Sort validatedSort(Sort requestedSort, Set<String> allowedFields) {
        for (Sort.Order order : requestedSort) {
            if (!allowedFields.contains(order.getProperty()) || order.isIgnoreCase()) {
                throw new IllegalArgumentException("Unsupported sort field or option");
            }
        }
        Sort sort = requestedSort;
        if (sort.getOrderFor(ID_PROPERTY) == null) {
            sort = sort.and(DEFAULT_SORT);
        }
        return sort;
    }
}

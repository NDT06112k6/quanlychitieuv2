package com.example.expensetracker.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableUtil {

    public static final int MAX_PAGE_SIZE = 100;
    private static final String ID_PROPERTY = "id";

    private PageableUtil() {
    }

    public static Pageable bounded(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            throw new IllegalArgumentException("Pagination is required");
        }
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Direction.DESC, ID_PROPERTY);
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);
        return boundedPageable;
    }
}

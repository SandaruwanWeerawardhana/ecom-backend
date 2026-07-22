package org.psint.beyosclothing.common.utils;

import org.springframework.data.domain.Page;
import org.psint.beyosclothing.common.dto.PageResponse;

import java.util.List;

/**
 * Pagination Utility
 * Helper methods for pagination handling
 */
public class PaginationUtils {

    public static <T> PageResponse<T> createPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .empty(page.isEmpty())
                .build();
    }

    public static <T> PageResponse<T> createPageResponse(List<T> content, int pageNumber,
                                                          int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return PageResponse.<T>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(pageNumber >= totalPages - 1)
                .first(pageNumber == 0)
                .empty(content.isEmpty())
                .build();
    }

    private PaginationUtils() {
        // Private constructor to prevent instantiation
    }
}


package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination metadata for report list responses, shaped to match the
 * frontend contract ({@code page}, {@code limit}, {@code total}, {@code totalPages}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPaginationResponse {
    private int page;
    private int limit;
    private long total;
    private int totalPages;
}

package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated payload for the Sales Details table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSalesDetailPageResponse {
    private List<ItemSalesDetailResponse> salesDetails;
    private ReportPaginationResponse pagination;
}

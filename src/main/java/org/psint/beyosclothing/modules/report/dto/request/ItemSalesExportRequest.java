package org.psint.beyosclothing.modules.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST body for the item sales details PDF export endpoint.
 * Mirrors the filter params of the GET sales-details endpoint, minus pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSalesExportRequest {
    private String startDate;   // YYYY-MM-DD, optional (default today)
    private String endDate;     // YYYY-MM-DD, optional (default today)
    private String startTime;   // HH:mm, optional (default 00:00)
    private String endTime;     // HH:mm, optional (default 23:59)
    private String search;      // optional product-name search
    private String type;        // optional SIMPLE | VARIABLE
    private String categoryId;  // optional numeric category id
}

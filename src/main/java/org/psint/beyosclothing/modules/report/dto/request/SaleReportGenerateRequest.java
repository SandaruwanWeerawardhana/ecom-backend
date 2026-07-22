package org.psint.beyosclothing.modules.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for generating a downloadable sales report file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportGenerateRequest {
    private String filter;
    private String startDate;
    private String startTime;
    private String endDate;
    private String endTime;
    private String month;
    private String format;
}

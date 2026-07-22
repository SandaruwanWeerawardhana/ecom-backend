package org.psint.beyosclothing.modules.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query filters used by the sales report page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportFilterRequest {
    private String period;       // daily | weekly | monthly
    private String startDate;    // yyyy-MM-dd
    private String startTime;    // HH:mm
    private String endDate;      // yyyy-MM-dd
    private String endTime;      // HH:mm
    private String month;        // yyyy-MM
    private String format;       // export format
}

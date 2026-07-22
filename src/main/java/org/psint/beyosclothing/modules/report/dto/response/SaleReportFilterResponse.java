package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportFilterResponse {
    private String startDate;
    private String endDate;
    private String month;
    private String startTime;
    private String endTime;
}

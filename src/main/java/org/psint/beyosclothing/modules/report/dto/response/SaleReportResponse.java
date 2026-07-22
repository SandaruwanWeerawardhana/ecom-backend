package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportResponse {
    private String period;
    private SaleReportFilterResponse filters;
    private SaleReportSummaryResponse summary;
    private SaleReportChartsResponse charts;
    private List<SaleReportDetailResponse> rows;
}

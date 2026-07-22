package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportDetailResponse {
    private String date;
    private String day;
    private String time;
    private String productUuid;
    private String product;
    private String category;
    private BigDecimal price;
    private Long qty;
    private BigDecimal totalRevenue;
}

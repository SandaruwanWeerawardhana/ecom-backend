package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One point on the item chart. {@code date} is the bucket key (date for
 * multi-day ranges, the report date for a single-day range) and {@code label}
 * is the display text the chart axis renders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemChartPointResponse {
    private String date;
    private String label;
    private BigDecimal revenue;
    private Long itemsSold;
}

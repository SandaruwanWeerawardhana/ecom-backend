package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One bar in the "Daily Orders" chart: the order volume and revenue for a
 * single day within the last 7 days (rolling window ending today).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyOrderCountResponse {
    private String date;
    private String day;
    private long orders;
    private BigDecimal revenue;
}

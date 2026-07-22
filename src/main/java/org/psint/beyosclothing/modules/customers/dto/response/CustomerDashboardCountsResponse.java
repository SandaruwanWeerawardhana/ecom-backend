package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDashboardCountsResponse {
    private Long totalOrdersCount;
    private Long pendingOrdersCount;
    private Long completedOrdersCount;
    private Long wishlistItemsCount;
}

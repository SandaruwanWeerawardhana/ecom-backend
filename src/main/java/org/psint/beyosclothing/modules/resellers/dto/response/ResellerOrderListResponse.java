package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Reseller Order List Response (Paginated)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of reseller orders")
public class ResellerOrderListResponse {

    @Schema(description = "List of orders")
    private List<OrderSummary> orders;

    @Schema(description = "Current page number")
    private Integer currentPage;

    @Schema(description = "Total pages")
    private Integer totalPages;

    @Schema(description = "Total orders")
    private Long totalOrders;

    @Schema(description = "Page size")
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        @Schema(description = "Order UUID")
        private String orderUuid;

        @Schema(description = "Order number")
        private String orderNumber;

        @Schema(description = "Order date")
        private LocalDateTime orderDate;

        @Schema(description = "Customer name")
        private String customerName;

        @Schema(description = "Total amount")
        private BigDecimal total;

        @Schema(description = "Profit/margin")
        private BigDecimal profit;

        @Schema(description = "Order status")
        private String status;

        @Schema(description = "Payment status")
        private String paymentStatus;

        @Schema(description = "Reason for cancellation")
        private String reasonForCancellation;
    }
}


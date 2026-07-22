package org.psint.beyosclothing.modules.orders.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Admin Order List
 * Used in paginated order listing with filters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderListResponse {

    private String orderUuid;
    private String orderNumber;
    private LocalDateTime orderDate;
    private String customerName;       // from order_shipping_address.full_name
    private BigDecimal subtotal;
    private BigDecimal total;
    private String orderType;          // ONLINE / POS  (from OrderEntity.OrderSource)
    private String orderFrom;          // CUSTOMER / RESELLER
    private String status;             // OrderStatus enum value
    private String paymentStatus;      // PaymentStatus enum value
    private String orderFromName;
}


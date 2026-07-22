package org.psint.beyosclothing.modules.orders.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for order placement
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacementResponse {

    private String orderUuid;
    private String orderNumber;
    private String status;
    private String paymentStatus;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal shippingCost;
    private BigDecimal total;

    private String paymentRedirectUrl; // For online payment gateways
    private String paymentMethodCode;
    private String paymentMethodName;

    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime createdAt;

    private String message;
}

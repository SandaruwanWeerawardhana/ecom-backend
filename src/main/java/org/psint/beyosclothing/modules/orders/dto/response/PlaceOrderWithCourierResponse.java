package org.psint.beyosclothing.modules.orders.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for placing an order with courier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderWithCourierResponse {

    /**
     * Order UUID
     */
    private String orderUuid;

    /**
     * Order number
     */
    private String orderNumber;

    /**
     * Shipment UUID
     */
    private String shipmentUuid;

    /**
     * Waybill ID assigned to this shipment
     */
    private String wayBillId;

    /**
     * Courier name
     */
    private String courierName;

    /**
     * Order status after placing with courier
     */
    private String orderStatus;

    /**
     * Shipment status
     */
    private String shipmentStatus;

    /**
     * Expected delivery date (3-5 days from now)
     */
    private LocalDateTime expectedDeliveryDate;

    /**
     * COD amount (if applicable)
     */
    private BigDecimal codAmount;

    /**
     * Message from Koombiyo API (if available)
     */
    private String courierApiMessage;

    /**
     * Timestamp when order was placed with courier
     */
    private LocalDateTime bookedAt;

    /**
     * Success indicator
     */
    private Boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;
}


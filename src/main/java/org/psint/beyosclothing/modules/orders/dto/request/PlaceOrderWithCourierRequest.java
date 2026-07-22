package org.psint.beyosclothing.modules.orders.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for placing an order with courier
 * Admin API to place already-created orders with a courier service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderWithCourierRequest {

    /**
     * Order UUID to place with courier
     */
    @NotBlank(message = "Order UUID is required")
    private String orderUuid;

    /**
     * Koombiyo waybill ID (barcode) allocated for this shipment
     */
    @NotBlank(message = "Waybill ID is required")
    @Size(min = 1, max = 100, message = "Waybill ID must be between 1 and 100 characters")
    private String wayBillId;

    /**
     * Special notes/remarks for the courier
     */
    @Size(max = 500, message = "Special notes cannot exceed 500 characters")
    private String specialNotes;

    /**
     * COD (Cash On Delivery) amount if applicable
     * If null, customer has not yet paid and will pay to courier
     */
    private java.math.BigDecimal codAmount;
}


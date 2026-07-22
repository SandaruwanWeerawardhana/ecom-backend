package org.psint.beyosclothing.modules.orders.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for placing an order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {

    /**
     * List of cart item UUIDs to order (if null/empty, all cart items are ordered)
     */
    @Size(min = 1, message = "At least one cart item must be selected")
    private List<String> cartItemUuids;

    /**
     * Delivery address ID from customer module
     */
    @NotNull(message = "Delivery address is required")
    private Long deliveryAddressId;

    /**
     * Courier ID for shipping
     */
    @NotNull(message = "Courier selection is required")
    private Long courierId;

    /**
     * Payment method ID
     */
    @NotNull(message = "Payment method is required")
    private Long paymentMethodId;

    /**
     * Customer notes/special instructions
     */
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String customerNotes;

    /**
     * Use promo code from cart if true (default: true)
     */
    @Builder.Default
    private Boolean usePromoCode = true;

    private String customerUuid;
}


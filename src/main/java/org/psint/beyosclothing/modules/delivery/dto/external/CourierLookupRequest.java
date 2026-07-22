package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for courier lookup via RabbitMQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierLookupRequest implements Serializable {
    private String courierUuid;
    private List<CartItemData> cartItems;
    private Long paymentMethodId;
    private String customerType; // RESELLER, CUSTOMER, etc.

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CartItemData implements Serializable {
        private Long productId;
        private Long variantId;
        private Integer quantity;
    }
}

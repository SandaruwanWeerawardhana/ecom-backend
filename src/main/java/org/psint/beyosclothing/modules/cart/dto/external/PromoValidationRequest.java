package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request to validate promo code from Promotion module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoValidationRequest implements Serializable {
    private String requestId;
    private String promoCode;
    private Long customerId;
    private BigDecimal cartSubtotal;
    private List<CartItemInfo> cartItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemInfo implements Serializable {
        private Long productId;
        private Long variantId;
        private Long categoryId; // ✅ Added for category-based promotions
        private Integer quantity;
        private BigDecimal price;
    }
}

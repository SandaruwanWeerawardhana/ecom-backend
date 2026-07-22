package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for cart items checkout request
 * Sent back to Payment module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemsCheckoutResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long cartId;
    private String cartUuid;
    private List<CartItemInfo> items;
    private String promoCode;
    private BigDecimal promoDiscount;
    private BigDecimal subtotal;
    private BigDecimal totalWeight; // Total weight of all items in kg

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String itemUuid;
        private Long productId;
        private String productUuid;
        private String productTitle;
        private Long variantId;
        private String variantUuid;
        private String variantTitle;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal itemWeight; // Weight per item in kg
        private BigDecimal totalItemWeight; // itemWeight * quantity
        private String imageUrl;
    }
}


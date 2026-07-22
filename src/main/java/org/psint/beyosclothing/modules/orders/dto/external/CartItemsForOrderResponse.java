package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for cart items lookup for order placement
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemsForOrderResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long cartId;
    private String cartUuid;
    private List<CartItemInfo> items;
    private String promoCode;
    private BigDecimal promoDiscount;
    private BigDecimal subtotal;
    private BigDecimal totalWeight;

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
        private BigDecimal itemWeight;
        private BigDecimal totalItemWeight;
        private String imageUrl;
        private Integer availableStock;
    }
}


package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Shared Response DTO for cart items lookup
 * Used by Cart module (sender/replier) and Payment/Order modules (receiver) via RabbitMQ
 * MUST be in shared package to avoid __TypeId__ class mismatch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemsLookupResponse implements Serializable {
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

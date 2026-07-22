package org.psint.beyosclothing.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for checkout preparation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Checkout summary response")
public class CheckoutResponse {

    @Schema(description = "Cart items selected for checkout")
    private List<CheckoutItemDTO> items;

    @Schema(description = "Delivery address information")
    private DeliveryAddressDTO deliveryAddress;

    @Schema(description = "Courier information")
    private CourierDTO courier;

    @Schema(description = "Payment method information")
    private PaymentMethodDTO paymentMethod;

    @Schema(description = "Price breakdown")
    private PriceBreakdownDTO priceBreakdown;

    @Schema(description = "Applied promo code (if any)")
    private String promoCode;

    @Schema(description = "Delivery notes")
    private String deliveryNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutItemDTO {
        private String itemUuid;
        private String productUuid;
        private String productTitle;
        private String variantUuid;
        private String variantTitle;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal itemWeight;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryAddressDTO {
        private String fullName;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String province;
        private String postalCode;
        private String phoneNumber;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierDTO {
        private Long courierId;
        private String courierName;
        private BigDecimal shippingCost;
        private Boolean isFreeShipping;
        private Map<String, Object> shippingBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodDTO {
        private Long paymentMethodId;
        private String paymentMethodName;
        private String paymentMethodCode;
        private String paymentMethodType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceBreakdownDTO {
        private BigDecimal subtotal;
        private BigDecimal promoDiscount;
        private BigDecimal shippingCost;
        private BigDecimal totalWeight;
        private BigDecimal total;
    }
}


package org.psint.beyosclothing.modules.orders.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for order details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {

    private String orderUuid;
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private String paymentMethod;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal shippingCost;
    private BigDecimal total;

    private String promoCode;
    private BigDecimal promoDiscount;

    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<OrderItemInfo> items;
    private ShippingAddressInfo shippingAddress;
    private TrackingInfo tracking;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private String uuid;
        private String productTitle;
        private String variantTitle;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal itemWeight;
        private Boolean isRefunded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressInfo {
        private String fullName;
        private String phoneNumber;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingInfo {
        private String courierName;
        private String trackingNumber;
        private String trackingUrl;
        private String shipmentStatus;
        private LocalDateTime bookedAt;
        private LocalDateTime shippedAt;
        private LocalDateTime deliveredAt;
    }
}


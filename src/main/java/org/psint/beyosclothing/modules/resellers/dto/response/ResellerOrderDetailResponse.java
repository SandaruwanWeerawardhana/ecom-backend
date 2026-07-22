package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Reseller Order Detail Response (Complete order information)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete reseller order details")
public class ResellerOrderDetailResponse {

    @Schema(description = "Order UUID")
    private String orderUuid;

    @Schema(description = "Order number")
    private String orderNumber;

    @Schema(description = "Order date")
    private LocalDateTime orderDate;

    @Schema(description = "Order status")
    private String orderStatus;

    @Schema(description = "Order from")
    private String orderFrom;

    @Schema(description = "Order type")
    private String orderType;

    @Schema(description = "Transaction ID")
    private String transactionId;

    @Schema(description = "Customer information")
    private CustomerInfo customer;

    @Schema(description = "Shipping address")
    private ShippingAddressDetail shippingAddress;

    @Schema(description = "Order items with margins")
    private List<OrderItemInfo> items;

    @Schema(description = "Subtotal")
    private BigDecimal subtotal;

    @Schema(description = "Shipping cost")
    private BigDecimal shippingCost;

    @Schema(description = "Discount total")
    private BigDecimal discountTotal;

    @Schema(description = "Tax")
    private BigDecimal tax;

    @Schema(description = "Total amount")
    private BigDecimal total;

    @Schema(description = "Payment information")
    private PaymentInfo payment;

    @Schema(description = "Tracking information")
    private TrackingInfo tracking;

    @Schema(description = "Order history")
    private List<OrderHistoryEntry> orderHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private String fullName;
        private String phone;
        private String email;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String district;
        private String province;
        private String postalCode;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressDetail {
        private String line1;
        private String line2;
        private String city;
        private String district;
        private String province;
        private String postalCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        @Schema(description = "Product name")
        private String productName;

        @Schema(description = "Variant name")
        private String variantName;

        @Schema(description = "Unit price (selling price)")
        private BigDecimal unitPrice;

        @Schema(description = "Base price (cost)")
        private BigDecimal basePrice;

        @Schema(description = "Quantity")
        private Integer quantity;

        @Schema(description = "Total price")
        private BigDecimal totalPrice;

        @Schema(description = "Margin")
        private BigDecimal margin;

        @Schema(description = "Is refunded")
        private Boolean isRefunded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        @Schema(description = "Payment method")
        private String paymentMethod;

        @Schema(description = "Payment status")
        private String paymentStatus;

        @Schema(description = "Transaction ID")
        private String transactionId;

        @Schema(description = "Paid amount")
        private BigDecimal paidAmount;

        @Schema(description = "Payment date")
        private LocalDateTime paymentDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingInfo {

        @Schema(description = "Shipped at")
        private LocalDateTime shippedAt;

        @Schema(description = "Delivered at")
        private LocalDateTime deliveredAt;

        @Schema(description = "Expected delivery date")
        private LocalDate expectedDelivery;

        @Schema(description = "Courier company")
        private String courierCompany;

        @Schema(description = "Tracking number")
        private String trackingNumber;

        @Schema(description = "Current status")
        private String currentStatus;

        private String carrier;

        private String trackingUrl; // formatted string e.g. "2024-12-20 - 2024-12-22"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderHistoryEntry {
        @Schema(description = "Status")
        private String status;

        @Schema(description = "Timestamp")
        private LocalDateTime timestamp;

        @Schema(description = "Changed by")
        private String changedBy;

        @Schema(description = "Notes")
        private String notes;
    }
}

package org.psint.beyosclothing.modules.orders.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Admin View Order Detail
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminOrderDetailResponse {

    // ── Core Order Info ────────────────────────────────────────────
    private String orderUuid;
    private String orderNumber;
    private LocalDateTime orderDate;
    private String orderStatus;
    private String orderFrom;       // CUSTOMER or RESELLER
    private String orderType;       // ONLINE or POS
    private String transactionId;   // paymentReference

    // ── Customer / Shipping Info ──────────────────────────────────
    private CustomerInfo customer;

    // ── Order Items ────────────────────────────────────────────────
    private List<OrderItemInfo> items;

    // ── Financials ─────────────────────────────────────────────────
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal discountTotal;
    private BigDecimal tax;
    private BigDecimal total;

    // ── Payment ────────────────────────────────────────────────────
    private PaymentInfo payment;

    // ── Tracking ───────────────────────────────────────────────────
    private TrackingInfo tracking;

    // ── Order History ──────────────────────────────────────────────
    private List<OrderHistoryEntry> orderHistory;

    // ─────────────────────────────────────────────────────────────────
    // Nested classes
    // ─────────────────────────────────────────────────────────────────

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
        private String province;
        private String postalCode;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private String productName;
        private String variantName;   // e.g. "Size: L | Color: Blue"
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal totalPrice;
        private Boolean isRefunded;
        // Reseller-specific (null for customer orders)
        private BigDecimal basePrice;
        private BigDecimal margin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private String paymentMethod;
        private String paymentStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingInfo {
        private String carrier;
        private String trackingNumber;
        private String trackingUrl;
        private String expectedDelivery;   // formatted string e.g. "2024-12-20 - 2024-12-22"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderHistoryEntry {
        private String status;
        private String description;
        private LocalDateTime timestamp;
    }
}


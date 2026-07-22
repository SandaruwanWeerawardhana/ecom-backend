package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Reseller Order Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller order details")
public class ResellerOrderResponse {

    @Schema(description = "Order UUID")
    private String orderUuid;

    @Schema(description = "Order number")
    private String orderNumber;

    @Schema(description = "Order date")
    private LocalDateTime orderDate;

    @Schema(description = "Customer name")
    private String customerName;

    @Schema(description = "Customer phone")
    private String customerPhone;

    @Schema(description = "Customer email")
    private String customerEmail;

    @Schema(description = "Shipping address")
    private ShippingAddressDetail shippingAddress;

    @Schema(description = "Order items with margins")
    private List<OrderItemDetail> items;

    @Schema(description = "Subtotal")
    private BigDecimal subtotal;

    @Schema(description = "Delivery charges")
    private BigDecimal deliveryCharges;

    @Schema(description = "Total amount")
    private BigDecimal total;

    @Schema(description = "Total profit/margin")
    private BigDecimal totalProfit;

    @Schema(description = "Payment status")
    private String paymentStatus;

    @Schema(description = "Order status")
    private String orderStatus;

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
    public static class OrderItemDetail {
        @Schema(description = "Product name")
        private String productName;

        @Schema(description = "Variant name")
        private String variantName;

        @Schema(description = "Base price (cost)")
        private BigDecimal basePrice;

        @Schema(description = "Selling price")
        private BigDecimal sellingPrice;

        @Schema(description = "Quantity")
        private Integer quantity;

        @Schema(description = "Total price")
        private BigDecimal totalPrice;

        @Schema(description = "Margin per item")
        private BigDecimal margin;
    }
}


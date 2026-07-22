package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {

    private String orderUuid;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private BigDecimal amount;
    private String type;
    private String status;
    private String paymentRedirectUrl; // OnePay hosted-checkout URL the frontend redirects to (null for offline methods)
    private String ipgTransactionId; // OnePay gateway transaction id, for later verification
    private List<OrderItemResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String productName;
        private String variantName;
        private BigDecimal unitPrice;
        private BigDecimal basePrice;
        private Integer quantity;
        private BigDecimal totalPrice;
        private BigDecimal margin;
        private Boolean isRefunded;
    }
}

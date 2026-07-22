package org.psint.beyosclothing.modules.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Courier Rate Response DTO
 * Used for API responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourierRateResponse {

    private String uuid;
    private List<Long> courierIds;
    private List<CourierResponse> couriers;
    private PaymentMethodFeeEntity.CustomerType customerType;
    private Long paymentMethodId;
    private BigDecimal firstKgPrice;
    private BigDecimal additionalKgPrice;
    private String weightGranularity;
    private BigDecimal minCharge;
    private BigDecimal maxCharge;
    private LocalDateTime effectiveTo;
    private LocalDateTime effectiveFrom;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

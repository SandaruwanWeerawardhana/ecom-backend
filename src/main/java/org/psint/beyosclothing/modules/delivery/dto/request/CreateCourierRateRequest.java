package org.psint.beyosclothing.modules.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateCourierRateRequest {

    @NotNull(message = "Courier UUID is required")
    private String courierUuid;

    @NotNull(message = "Customer type is required")
    private PaymentMethodFeeEntity.CustomerType customerType;

    private Long paymentMethodId;

    @NotNull(message = "First kg price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "First kg price must be greater than 0")
    @Positive(message = "First kg price must be positive")
    private BigDecimal firstKgPrice;

    @NotNull(message = "Additional kg price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Additional kg price must be greater than 0")
    @Positive(message = "Additional kg price must be positive")
    private BigDecimal additionalKgPrice;

    @NotNull(message = "Weight granularity is required")
    private String weightGranularity;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum charge must be greater than or equal to 0")
    private BigDecimal minCharge;

    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum charge must be greater than or equal to 0")
    private BigDecimal maxCharge;

    @NotNull(message = "Effective from date is required")
    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;
}



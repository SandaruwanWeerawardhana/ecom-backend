package org.psint.beyosclothing.modules.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCourierRateRequest {

    private String courierUuid;

    private PaymentMethodFeeEntity.CustomerType customerType;

    private Long paymentMethodId;

    @DecimalMin(value = "0.0", inclusive = false, message = "First kg price must be greater than 0")
    @Positive(message = "First kg price must be positive")
    private BigDecimal firstKgPrice;

    @DecimalMin(value = "0.0", inclusive = false, message = "Additional kg price must be greater than 0")
    @Positive(message = "Additional kg price must be positive")
    private BigDecimal additionalKgPrice;

    private String weightGranularity;

    @DecimalMin(value = "0.0", message = "Minimum charge must be greater than or equal to 0")
    private BigDecimal minCharge;

    @DecimalMin(value = "0.0", message = "Maximum charge must be greater than or equal to 0")
    private BigDecimal maxCharge;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    private Boolean isActive;
}


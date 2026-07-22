package org.psint.beyosclothing.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;

/**
 * DTO for creating a new payment method fee
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new payment method fee")
public class CreatePaymentMethodFeeRequest {

    @Schema(description = "Payment method UUID (set by controller from path variable)", hidden = true)
    private String paymentMethodUuid;

    @NotNull(message = "Customer type is required")
    @Schema(description = "Customer type (CUSTOMER, RESELLER, or BOTH)", example = "CUSTOMER")
    private PaymentMethodFeeEntity.CustomerType customerType;

    @NotNull(message = "Fee type is required")
    @Schema(description = "Fee type (FIXED or PERCENTAGE)", example = "FIXED")
    private PaymentMethodFeeEntity.FeeType feeType;

    @NotNull(message = "Fee value is required")
    @DecimalMin(value = "0.0", message = "Fee value must be greater than or equal to 0")
    @Schema(description = "Fee value (amount for FIXED, percentage for PERCENTAGE)", example = "10.00")
    private BigDecimal feeValue;

    @Schema(description = "Active status (defaults to true if not provided)", example = "true")
    private Boolean isActive;

    @Schema(description = "Free shipping flag (defaults to false if not provided)", example = "false")
    private Boolean isFreeShipping;
}


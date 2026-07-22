package org.psint.beyosclothing.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;

/**
 * DTO for updating an existing payment method fee
 * All fields are optional for partial updates
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update a payment method fee")
public class UpdatePaymentMethodFeeRequest {

    @Schema(description = "Customer type (CUSTOMER, RESELLER, or BOTH)", example = "CUSTOMER")
    private PaymentMethodFeeEntity.CustomerType customerType;

    @Schema(description = "Fee type (FIXED or PERCENTAGE)", example = "FIXED")
    private PaymentMethodFeeEntity.FeeType feeType;

    @DecimalMin(value = "0.0", message = "Fee value must be greater than or equal to 0")
    @Schema(description = "Fee value (amount for FIXED, percentage for PERCENTAGE)", example = "10.00")
    private BigDecimal feeValue;

    @Schema(description = "Active status (defaults to true if not provided)", example = "true")
    private Boolean isActive;

    @Schema(description = "Free shipping flag (defaults to false if not provided)", example = "false")
    private Boolean isFreeShipping;
}


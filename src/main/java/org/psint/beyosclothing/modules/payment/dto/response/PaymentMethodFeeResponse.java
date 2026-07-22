package org.psint.beyosclothing.modules.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for payment method fee response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment method fee response")
public class PaymentMethodFeeResponse {

    @Schema(description = "Payment method ID", example = "1")
    private Long methodId;

    @Schema(description = "Unique identifier for the fee", example = "123e4567-e89b-12d3-a456-426614174000")
    private String uuid;

    @Schema(description = "Customer type", example = "CUSTOMER")
    private PaymentMethodFeeEntity.CustomerType customerType;

    @Schema(description = "Fee type", example = "FIXED")
    private PaymentMethodFeeEntity.FeeType feeType;

    @Schema(description = "Fee value", example = "10.00")
    private BigDecimal feeValue;

    @Schema(description = "Active status", example = "true")
    private Boolean isActive;

    @Schema(description = "Free shipping flag", example = "false")
    private Boolean isFreeShipping;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}


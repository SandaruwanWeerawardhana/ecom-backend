package org.psint.beyosclothing.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePaymentMethodRequest {

    private String uuid;

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(min = 2, max = 50, message = "Code must be between 2 and 50 characters")
    private String code;

    @Schema(description = "Type of payment method", allowableValues = {"ONLINE", "OFFLINE", "POS"})
    private PaymentMethodEntity.PaymentType type;

    @Schema(description = "Whether the payment method is active", example = "true", defaultValue = "true")
    private Boolean isActive;

    private Boolean supportsRefund;

    @Schema(description = "Whether the payment method supports callbacks/webhooks", example = "true", defaultValue = "false")
    private Boolean supportsCallback;

    @Schema(description = "Whether courier/shipping fee is free with this payment method", example = "false", defaultValue = "false")
    private Boolean isCourierFeeFree;
}

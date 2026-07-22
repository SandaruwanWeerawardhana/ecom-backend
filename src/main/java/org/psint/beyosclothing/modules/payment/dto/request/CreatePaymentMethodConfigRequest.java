package org.psint.beyosclothing.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new payment method configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new payment method configuration")
public class CreatePaymentMethodConfigRequest {

    private Long methodId;

    @NotBlank(message = "Label is required")
    @Size(max = 100, message = "Label must not exceed 100 characters")
    @Schema(description = "Display label for the configuration")
    private String label;

    @NotBlank(message = "Config key is required")
    @Size(max = 100, message = "Config key must not exceed 100 characters")
    @Schema(description = "Unique configuration key")
    private String configKey;

    @NotBlank(message = "Config value is required")
    @Schema(description = "Configuration value (will be encrypted if marked as secret)")
    private String configValue;

    @Schema(description = "Whether this value is a secret (will be masked in responses)")
    private Boolean isSecret = true;
}


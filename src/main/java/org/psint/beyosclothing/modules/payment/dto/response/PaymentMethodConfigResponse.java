package org.psint.beyosclothing.modules.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for payment method configuration response
 * Secret values are masked for security
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment method configuration response")
public class PaymentMethodConfigResponse {

    @Schema(description = "Config ID")
    private Long id;

    @Schema(description = "Payment method ID", example = "1")
    private Long methodId;

    @Schema(description = "Display label", example = "API Key")
    private String label;

    @Schema(description = "Configuration key", example = "api_key")
    private String configKey;

    @Schema(description = "Configuration value (masked if secret)", example = "sk_live_****xxx")
    private String configValue;

    @Schema(description = "Whether this is a secret value", example = "true")
    private Boolean isSecret;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;


}


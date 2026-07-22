package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for Updating Cart Item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update cart item")
public class UpdateCartItemRequest {

    @NotBlank(message = "Cart item UUID is required")
    @Schema(description = "Cart item UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String cartItemUuid;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Updated quantity", example = "3")
    private Integer quantity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Override price must be positive")
    @Schema(description = "Custom selling price (optional)", example = "2500.00")
    private BigDecimal overridePrice;
}


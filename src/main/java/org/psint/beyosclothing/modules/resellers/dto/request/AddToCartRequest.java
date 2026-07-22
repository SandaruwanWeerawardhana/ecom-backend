package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for Adding Item to Reseller Cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to add product to reseller cart")
public class AddToCartRequest {

    @NotBlank(message = "Product UUID is required")
    @Schema(description = "Product UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String productUuid;

    @Schema(description = "Variant UUID (nullable for simple products)", example = "123e4567-e89b-12d3-a456-426614174001")
    private String variantUuid;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Quantity to add", example = "5")
    private Integer quantity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Override price must be positive")
    @Schema(description = "Custom selling price (optional)", example = "2500.00")
    private BigDecimal overridePrice;
}


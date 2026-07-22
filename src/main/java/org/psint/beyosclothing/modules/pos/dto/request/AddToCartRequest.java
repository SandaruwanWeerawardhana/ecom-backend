package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POS Add to Cart Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "productId is required")
    private String productUuid;

    // Optional for simple products
    private String variantUuid;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    private Integer quantity;

    // Optional price snapshot when adding item (use product price if not provided)
    @DecimalMin(value = "0.0", inclusive = true, message = "unitPrice must be >= 0")
    private Double unitPrice;

    // Optional stock snapshot
    @PositiveOrZero(message = "stockAvailable must be >= 0")
    private Integer stockAvailable;

    private String customerType;
}


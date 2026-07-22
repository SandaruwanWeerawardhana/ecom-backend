package org.psint.beyosclothing.modules.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating stock quantity via the REST API.
 * movementType: IN | OUT | ADJUSTMENT | RETURN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStockRequest {

    /**
     * Quantity to apply.
     * IN / RETURN  → added to current stock.
     * OUT          → subtracted from current stock.
     * ADJUSTMENT   → set as the new absolute stock value.
     */
    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be 0 or greater")
    private Integer quantity;

    /**
     * Movement type: IN, OUT, ADJUSTMENT, RETURN
     */
    @NotBlank(message = "movementType is required")
    private String movementType;

    /** Who performed this update (username / system label). */
    private String performedBy;

    /** Optional free-text notes. */
    private String notes;
}


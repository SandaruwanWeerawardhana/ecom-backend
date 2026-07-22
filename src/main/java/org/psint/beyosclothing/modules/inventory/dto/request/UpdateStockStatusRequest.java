package org.psint.beyosclothing.modules.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateStockStatusRequest {

    /**
     * Target inventory status.
     * Accepted values: IN_STOCK | OUT_OF_STOCK | ON_BACKORDER
     */
    @NotBlank(message = "status is required")
    @Pattern(regexp = "IN_STOCK|OUT_OF_STOCK|ON_BACKORDER",
             message = "status must be IN_STOCK, OUT_OF_STOCK or ON_BACKORDER")
    private String status;

    private String performedBy;

    private String notes;
}


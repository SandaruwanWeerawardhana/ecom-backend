package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update cart item request (POS)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {

    private String uuid;

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be 0 or greater (0 removes the item)")
    private Integer quantity;
}

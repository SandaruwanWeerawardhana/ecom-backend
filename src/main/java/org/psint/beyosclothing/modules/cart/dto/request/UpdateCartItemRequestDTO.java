package org.psint.beyosclothing.modules.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update Cart Item Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequestDTO {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}


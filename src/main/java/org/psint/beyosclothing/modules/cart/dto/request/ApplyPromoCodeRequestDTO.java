package org.psint.beyosclothing.modules.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apply Promo Code Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPromoCodeRequestDTO {

    @NotBlank(message = "Promo code is required")
    private String promoCode;
}


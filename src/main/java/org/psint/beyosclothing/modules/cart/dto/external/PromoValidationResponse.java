package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response from Promotion module with promo validation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoValidationResponse implements Serializable {
    private String requestId;
    private Long promotionId;
    private String promoCode;
    private Boolean isValid;
    private String discountType; // PERCENTAGE, FIXED_AMOUNT
    private BigDecimal discountValue;
    private BigDecimal calculatedDiscount;
    private String errorMessage;
    private Boolean found;
}


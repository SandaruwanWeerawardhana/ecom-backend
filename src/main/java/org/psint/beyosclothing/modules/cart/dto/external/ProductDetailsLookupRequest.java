package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for looking up product details by ID (not UUID)
 * Used by Cart module to fetch product details for cart items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailsLookupRequest implements Serializable {

    private String requestId;
    private Long productId;
    private Long variantId; // Optional - if cart item has variant
}


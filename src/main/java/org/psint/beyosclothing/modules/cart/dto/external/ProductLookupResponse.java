package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response from Product module with product/variant details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLookupResponse implements Serializable {
    private String requestId;

    // Product details
    private Long productId;
    private String productUuid;
    private String productTitle;
    private String productSku;
    private String thumbnailUrl;
    private BigDecimal showcasePrice;
    private BigDecimal salePrice;

    // Product type: SIMPLE or VARIABLE
    private String productType;

    // Variant details (if requested)
    private Long variantId;
    private String variantUuid;
    private String variantSku;
    private String variantAttributeSummary;
    private BigDecimal variantShowcasePrice;
    private BigDecimal variantSalePrice;
    private BigDecimal variantResellerPrice;

    // Wholesale pricing (resolved from variant: default variant for SIMPLE, requested variant for VARIABLE)
    // Both fields must be present for wholesale pricing to be applied
    private BigDecimal wholesalePrice;
    private Integer wholesaleMinQty;

    // Stock information
    private Integer stockAvailable;

    private Boolean found;
    private String errorMessage;
}

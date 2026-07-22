package org.psint.beyosclothing.modules.resellers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response from Product module with product/variant details
 * This is a duplicate DTO for cross-module communication via RabbitMQ
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

    // Variant details (if requested)
    private Long variantId;
    private String variantUuid;
    private String variantSku;
    private String variantAttributeSummary;
    private BigDecimal variantShowcasePrice;
    private BigDecimal variantSalePrice;

    // Stock information
    private Integer stockAvailable;

    private Boolean found;
    private String errorMessage;
}


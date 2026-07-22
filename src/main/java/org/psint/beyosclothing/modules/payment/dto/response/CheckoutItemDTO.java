package org.psint.beyosclothing.modules.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Individual checkout item details")
public class CheckoutItemDTO {

    @Schema(description = "Cart item UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String uuid;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Product title", example = "Classic Cotton T-Shirt")
    private String productTitle;

    @Schema(description = "Product SKU", example = "SKU-12345")
    private String productSku;

    @Schema(description = "Product thumbnail URL")
    private String thumbnailUrl;

    @Schema(description = "Variant ID if applicable", example = "10")
    private Long variantId;

    @Schema(description = "Variant attributes summary", example = "Size: L, Color: Blue")
    private String variantAttributeSummary;

    @Schema(description = "Quantity", example = "2")
    private Integer quantity;

    @Schema(description = "Original price per item", example = "50.00")
    private BigDecimal price;

    @Schema(description = "Sale price per item if on sale", example = "40.00")
    private BigDecimal salePrice;

    @Schema(description = "Line total (price x quantity)", example = "80.00")
    private BigDecimal lineTotal;

    @Schema(description = "Item weight in kg", example = "0.5")
    private BigDecimal weight;

    @Schema(description = "Is item on sale", example = "true")
    private Boolean isOnSale;

    @Schema(description = "Stock available for this item", example = "100")
    private Integer stockAvailable;

    @Schema(description = "Is item available for purchase", example = "true")
    private Boolean isAvailable;
}

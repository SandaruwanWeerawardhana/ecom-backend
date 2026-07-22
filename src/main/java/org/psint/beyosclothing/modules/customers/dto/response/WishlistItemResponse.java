package org.psint.beyosclothing.modules.customers.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.customers.entity.WishlistItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for customer wishlist items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WishlistItemResponse {

    private Long id;
    private String uuid;
    private Long customerId;
    private String customerUuid;
    private Long productId;
    private String productUuid;
    private String productTitle;
    private String productThumbnailUrl;
    private Long variantId;
    private String variantUuid;
    private String variantAttributeSummary;
    private WishlistItem.Priority priority;
    private String notes;
    private String tags;
    private BigDecimal priceSnapshot;
    private BigDecimal salePriceSnapshot;
    private LocalDateTime capturedAt;
    private Boolean isAvailable;
    private String source;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
}

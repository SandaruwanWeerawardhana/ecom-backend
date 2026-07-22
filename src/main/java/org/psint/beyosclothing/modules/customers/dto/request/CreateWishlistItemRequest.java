package org.psint.beyosclothing.modules.customers.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.customers.entity.WishlistItem;

/**
 * Request DTO for adding a product to a customer's wishlist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWishlistItemRequest {

    @NotBlank(message = "Product UUID is required")
    private String productUuid;

    private String variantUuid;

    private WishlistItem.Priority priority;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    private String tags;

    @Size(max = 50, message = "Source must not exceed 50 characters")
    private String source;
}

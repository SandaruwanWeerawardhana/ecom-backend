
package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request to check stock availability from Inventory module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckRequest implements Serializable {
    private String requestId;
    private Long productId;
    private Long variantId;
    private Integer requestedQuantity;
}

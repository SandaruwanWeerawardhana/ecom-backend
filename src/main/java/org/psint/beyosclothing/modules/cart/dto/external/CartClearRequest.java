
package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for clearing cart items (from Order module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartClearRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long cartId;
    private List<String> itemUuids; // If null, clear all items
}


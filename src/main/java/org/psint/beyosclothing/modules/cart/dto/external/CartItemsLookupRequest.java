package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for getting cart items for checkout
 * Received from Payment module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemsLookupRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private String customerUuid;
    private String guestSessionToken;
    private List<String> selectedItemUuids; // Optional: if null, get all items
}


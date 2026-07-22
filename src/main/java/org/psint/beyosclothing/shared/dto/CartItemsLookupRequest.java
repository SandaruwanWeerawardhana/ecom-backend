package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Shared Request DTO for getting cart items for checkout
 * Used by Payment module (sender) and Cart module (receiver) via RabbitMQ
 * MUST be in shared package to avoid __TypeId__ class mismatch
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

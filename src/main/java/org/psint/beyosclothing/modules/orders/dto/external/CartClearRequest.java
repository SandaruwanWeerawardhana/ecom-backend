package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

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


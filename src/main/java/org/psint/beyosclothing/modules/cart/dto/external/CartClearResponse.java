package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for cart clearing operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartClearResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean success;
    private Integer itemsCleared;
    private String errorMessage;
}


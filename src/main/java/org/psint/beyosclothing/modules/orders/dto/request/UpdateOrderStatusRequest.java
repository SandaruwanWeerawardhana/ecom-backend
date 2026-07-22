package org.psint.beyosclothing.modules.orders.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;

/**
 * Request DTO to update an order's status by admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Status is required")
    private OrderEntity.OrderStatus status;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}

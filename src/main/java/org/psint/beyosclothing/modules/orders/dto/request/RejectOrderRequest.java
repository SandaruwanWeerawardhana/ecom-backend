package org.psint.beyosclothing.modules.orders.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to reject an order by admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectOrderRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
    private String reason;

    @Size(max = 1000, message = "Admin notes cannot exceed 1000 characters")
    private String adminNotes;
}


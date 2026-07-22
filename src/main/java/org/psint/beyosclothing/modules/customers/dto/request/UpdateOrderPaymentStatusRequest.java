package org.psint.beyosclothing.modules.customers.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the customer-triggered payment status update endpoint.
 * Carries the payment status the client observed (e.g. UNPAID after an abandoned checkout).
 * PAID is not accepted from clients; a paid outcome is only confirmed via gateway verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderPaymentStatusRequest {

    @NotBlank(message = "Payment status is required")
    private String paymentStatus;
}

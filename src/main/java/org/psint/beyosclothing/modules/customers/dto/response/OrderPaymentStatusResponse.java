package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight payment-status view the frontend polls after an online checkout, while it waits for the
 * asynchronous gateway callback to settle the order. Kept minimal so it stays cheap to poll repeatedly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentStatusResponse {

    private String orderUuid;
    private String orderStatus;   // e.g. PENDING, PAID
    private String paymentStatus; // UNPAID, PAID or FAILED
}

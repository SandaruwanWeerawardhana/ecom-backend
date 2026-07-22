package org.psint.beyosclothing.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for preparing checkout
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Checkout preparation request")
public class CheckoutRequest {

    @Schema(description = "List of selected cart item UUIDs (if null, uses all selected items in cart)")
    private List<String> selectedItemUuids;

    @Schema(description = "Customer's delivery address ID (for authenticated customers)", example = "123")
    private Long addressId;

    @NotNull(message = "Courier ID is required")
    @Schema(description = "Selected courier service UUID", example = "113fdf08-91e6-4755-b28d-39bc28372495", required = true)
    private String courierUuid;

    @NotNull(message = "Payment method UUID is required")
    @Schema(description = "Selected payment method UUID", example = "abc-123-def", required = true)
    private String paymentMethodUuid;

    @Schema(description = "Delivery notes/instructions", example = "Please call before delivery")
    private String deliveryNotes;
}

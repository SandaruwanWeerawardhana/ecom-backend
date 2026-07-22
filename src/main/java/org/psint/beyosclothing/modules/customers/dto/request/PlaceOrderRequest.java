package org.psint.beyosclothing.modules.customers.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {


    @Size(min = 1, message = "At least one cart item must be selected")
    private List<String> cartItemUuids;

    @NotNull(message = "Delivery address is required")
    private Long deliveryAddressId;

    @NotNull(message = "Courier selection is required")
    private Long courierId;

    @NotNull(message = "Payment method is required")
    private Long paymentMethodId;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String customerNotes;

    @Builder.Default
    private Boolean usePromoCode = true;

    private String customerUuid;
}

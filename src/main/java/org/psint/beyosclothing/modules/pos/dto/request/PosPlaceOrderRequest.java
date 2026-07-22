package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosPlaceOrderRequest {

    @NotNull(message = "cartUuid is required")
    private String cartUuid;

    private Long customerId;

    @NotNull(message = "paymentMethodId is required")
    private Long paymentMethodId;

    // Optional: required when payment method is CARD - controller/service must enforce this
    @Pattern(regexp = "^\\d{4}$", message = "cardLastFourDigits must be exactly 4 digits")
    private String cardLastFourDigits;

    private String customerNotes;
}

package org.psint.beyosclothing.modules.pos.dto.request;

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
public class PosDeliveryOrderRequest {

    private List<String> cartItemUuids;

    private String source;

    private Long deliveryAddressId;

    private Long courierId;

    private String courierUuid;

    private Long paymentMethodId;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String customerNotes;

    @Builder.Default
    private Boolean usePromoCode = true;

    private String customerUuid;
}

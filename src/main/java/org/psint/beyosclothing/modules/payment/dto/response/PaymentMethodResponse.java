package org.psint.beyosclothing.modules.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PaymentMethodResponse {

    private String uuid;

    private String name;

    private String code;

    private PaymentMethodEntity.PaymentType type;

    private Boolean isActive;

    private Boolean supportsRefund;

    private Boolean supportsCallback;

    private Boolean isCourierFeeFree;
}

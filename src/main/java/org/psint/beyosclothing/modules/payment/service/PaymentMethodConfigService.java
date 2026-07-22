package org.psint.beyosclothing.modules.payment.service;

import jakarta.validation.Valid;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodConfigResponse;

import java.util.List;

public interface PaymentMethodConfigService {

    PaymentMethodConfigResponse addConfig(String paymentMethodUuid, @Valid CreatePaymentMethodConfigRequest request);

    PaymentMethodConfigResponse updateConfig(Long configId, @Valid UpdatePaymentMethodConfigRequest request);

    List<PaymentMethodConfigResponse> getConfigs(String paymentMethodUuid);

    void deleteConfig(Long configId);

    PaymentMethodConfigResponse getConfigById(Long configId);

    String getDecryptedConfigValue(Long methodId, String configKey);
}

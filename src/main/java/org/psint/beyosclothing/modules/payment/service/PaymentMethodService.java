package org.psint.beyosclothing.modules.payment.service;

import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;

import java.util.List;

public interface PaymentMethodService {

    PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request);

    PaymentMethodResponse updatePaymentMethod(String uuid, UpdatePaymentMethodRequest request);

    PaymentMethodResponse getPaymentMethodByUuid(String uuid);

    PaymentMethodResponse getPaymentMethodByCode(String code);

    List<PaymentMethodResponse> getAllPaymentMethods();

    List<PaymentMethodResponse> getActivePaymentMethods();

    List<PaymentMethodResponse> getPaymentMethodsByType(PaymentMethodEntity.PaymentType type);

    List<PaymentMethodResponse> getActivePaymentMethodsByType(PaymentMethodEntity.PaymentType type);

    void deletePaymentMethod(String uuid);
    
    PaymentMethodResponse togglePaymentMethodStatus(String uuid);
}

package org.psint.beyosclothing.modules.payment.service;

import jakarta.validation.Valid;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodFeeResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;

import java.math.BigDecimal;
import java.util.List;


public interface PaymentMethodFeeService {

    PaymentMethodFeeResponse addFee(@Valid CreatePaymentMethodFeeRequest request);

    PaymentMethodFeeResponse updateFee(Long feeId, @Valid UpdatePaymentMethodFeeRequest request);

    List<PaymentMethodFeeResponse> getFeesByMethod(String paymentMethodUuid);

    BigDecimal calculateFee(String paymentMethodUuid, PaymentMethodFeeEntity.CustomerType customerType, BigDecimal amount);

    void deleteFee(Long feeId);
}


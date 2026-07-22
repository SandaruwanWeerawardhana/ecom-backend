package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodFeeResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodFeeRepository;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodFeeService;
import org.psint.beyosclothing.modules.products.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentMethodFeeServiceImpl implements PaymentMethodFeeService {

    private final PaymentMethodFeeRepository feeRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    public PaymentMethodFeeResponse addFee(CreatePaymentMethodFeeRequest request) {
        log.info("Creating new payment method fee for payment method UUID: {}", request.getPaymentMethodUuid());

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(request.getPaymentMethodUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found UUID: " + request.getPaymentMethodUuid()));

        if (feeRepository.existsByPaymentMethodIdAndCustomerType(paymentMethod.getId(), request.getCustomerType())) {
            throw new DuplicateResourceException("Fee already exists for customer type: " + request.getCustomerType());
        }

        PaymentMethodFeeEntity feeEntity = PaymentMethodFeeEntity.builder()
                .paymentMethod(paymentMethod)
                .customerType(request.getCustomerType())
                .feeType(request.getFeeType() != null ? request.getFeeType() : PaymentMethodFeeEntity.FeeType.FIXED)
                .feeValue(request.getFeeValue())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .isFreeShipping(Boolean.TRUE.equals(request.getIsFreeShipping()))
                .build();

        PaymentMethodFeeEntity savedFee = feeRepository.save(feeEntity);
        log.info("Successfully created payment method fee with ID: {}", savedFee.getId());

        return mapToResponse(savedFee, paymentMethod);
    }

    @Override
    public PaymentMethodFeeResponse updateFee(Long feeId, UpdatePaymentMethodFeeRequest request) {
        log.info("Updating payment method fee with ID: {}", feeId);

        PaymentMethodFeeEntity feeEntity = feeRepository.findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method fee not found with ID: " + feeId));

        // Update fields if provided
        if (request.getCustomerType() != null) {
            feeEntity.setCustomerType(request.getCustomerType());
        }
        if (request.getFeeType() != null) {
            feeEntity.setFeeType(request.getFeeType());
        }
        if (request.getFeeValue() != null) {
            feeEntity.setFeeValue(request.getFeeValue());
        }
        if (request.getIsFreeShipping() != null) {
            feeEntity.setIsFreeShipping(request.getIsFreeShipping());
        }

        PaymentMethodFeeEntity updatedFee = feeRepository.save(feeEntity);
        log.info("Successfully updated payment method fee with ID: {}", feeId);

        return mapToResponse(updatedFee, updatedFee.getPaymentMethod());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodFeeResponse> getFeesByMethod(String paymentMethodUuid) {
        log.info("Fetching fees for payment method UUID: {}", paymentMethodUuid);

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(paymentMethodUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found with UUID: " + paymentMethodUuid));

        List<PaymentMethodFeeEntity> fees = feeRepository.findByPaymentMethodId(paymentMethod.getId());
        log.info("Found {} fees for payment method UUID: {}", fees.size(), paymentMethodUuid);

        return fees.stream()
                .map(fee -> mapToResponse(fee, fee.getPaymentMethod()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateFee(String paymentMethodUuid, PaymentMethodFeeEntity.CustomerType customerType, BigDecimal amount) {
        log.info("Calculating fee for payment method UUID: {}, customer type: {}, amount: {}",
                paymentMethodUuid, customerType, amount);

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(paymentMethodUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found with UUID: " + paymentMethodUuid));

        List<PaymentMethodFeeEntity> fees = feeRepository.findAllByPaymentMethodIdAndCustomerTypeAndIsActiveTrue(
                paymentMethod.getId(), customerType);

        if (fees.isEmpty()) {
            fees = feeRepository.findAllByPaymentMethodIdAndCustomerTypeAndIsActiveTrue(
                    paymentMethod.getId(), PaymentMethodFeeEntity.CustomerType.BOTH);
        }

        if (fees.isEmpty()) {
            log.info("No active fee found, returning 0");
            return BigDecimal.ZERO;
        }

        PaymentMethodFeeEntity fee = fees.getFirst();
        BigDecimal calculatedFee;

        if (fee.getFeeType() == PaymentMethodFeeEntity.FeeType.PERCENTAGE) {
            calculatedFee = amount.multiply(fee.getFeeValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            calculatedFee = fee.getFeeValue();
        }


        log.info("Calculated fee: {}", calculatedFee);
        return calculatedFee;
    }

    @Override
    public void deleteFee(Long feeId) {
        log.info("Deleting payment method fee with ID: {}", feeId);

        PaymentMethodFeeEntity feeEntity = feeRepository.findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method fee not found with ID: " + feeId));

        feeRepository.delete(feeEntity);
        log.info("Successfully deleted payment method fee with ID: {}", feeId);
    }

    private PaymentMethodFeeResponse mapToResponse(PaymentMethodFeeEntity fee, PaymentMethodEntity paymentMethod) {
        return PaymentMethodFeeResponse.builder()
                .methodId(paymentMethod != null ? paymentMethod.getId() : null)
                .uuid(fee.getUuid())
                .customerType(fee.getCustomerType())
                .feeType(fee.getFeeType())
                .feeValue(fee.getFeeValue())
                .isActive(fee.getIsActive())
                .isFreeShipping(fee.getIsFreeShipping())
                .createdAt(fee.getCreatedAt())
                .updatedAt(fee.getUpdatedAt())
                .build();
    }
}


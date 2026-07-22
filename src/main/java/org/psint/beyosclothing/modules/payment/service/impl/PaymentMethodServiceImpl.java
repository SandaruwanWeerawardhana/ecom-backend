package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodService;
import org.psint.beyosclothing.modules.products.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of PaymentMethodService
 * Handles all business logic for payment method operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private static final String PAYMENT_METHOD_NOT_FOUND_UUID = "Payment method not found with UUID: ";
    private static final String PAYMENT_METHOD_NOT_FOUND_UUID_LOG = "Payment method not found with UUID: {}";

    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    public PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request) {
        log.info("Creating new payment method with code: {}", request.getCode());

        if (paymentMethodRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Payment method with code '" + request.getCode() + "' already exists");
        }

        PaymentMethodEntity entity = PaymentMethodEntity.builder()
                .uuid(UUID.randomUUID().toString())
                .name(request.getName())
                .code(request.getCode())
                .type(request.getType())
                .isActive(request.getIsActive() != null && request.getIsActive())
                .supportsRefund(Boolean.TRUE.equals(request.getSupportsRefund()))
                .supportsCallback(Boolean.TRUE.equals(request.getSupportsCallback()))
                .isCourierFeeFree(Boolean.TRUE.equals(request.getIsCourierFeeFree()))
                .build();

        PaymentMethodEntity savedEntity = paymentMethodRepository.save(entity);
        log.info("Payment method created successfully with UUID: {}", savedEntity.getUuid());

        return mapToResponse(savedEntity);
    }

    @Override
    public PaymentMethodResponse updatePaymentMethod(String uuid, UpdatePaymentMethodRequest request) {
        log.info("Updating payment method with UUID: {}", uuid);

        PaymentMethodEntity entity = paymentMethodRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_UUID + uuid));

        if (request.getCode() != null && !request.getCode().equals(entity.getCode())) {
            if (paymentMethodRepository.existsByCode(request.getCode())) {
                log.error("Payment method with code {} already exists", request.getCode());
                throw new DuplicateResourceException("Payment method with code '" + request.getCode() + "' already exists");
            }
            entity.setCode(request.getCode());
        }

        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getType() != null) {
            entity.setType(request.getType());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getSupportsRefund() != null) {
            entity.setSupportsRefund(request.getSupportsRefund());
        }
        if (request.getSupportsCallback() != null) {
            entity.setSupportsCallback(request.getSupportsCallback());
        }
        if (request.getIsCourierFeeFree() != null) {
            entity.setIsCourierFeeFree(request.getIsCourierFeeFree());
        }

        PaymentMethodEntity updatedEntity = paymentMethodRepository.save(entity);
        log.info("Payment method updated successfully with UUID: {}", uuid);

        return mapToResponse(updatedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethodByUuid(String uuid) {
        log.debug("Fetching payment method by UUID: {}", uuid);

        PaymentMethodEntity entity = paymentMethodRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error(PAYMENT_METHOD_NOT_FOUND_UUID_LOG, uuid);
                    return new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_UUID + uuid);
                });

        if (Boolean.FALSE.equals(entity.getIsActive())) {
            log.error("Payment method with UUID {} is inactive", uuid);
            throw new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_UUID + uuid);
        }

        log.debug("Payment method found: {}", entity.getName());
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethodByCode(String code) {
        log.debug("Fetching payment method by code: {}", code);

        PaymentMethodEntity entity = paymentMethodRepository.findByCode(code)
                .orElseThrow(() -> {
                    log.error("Payment method not found with code: {}", code);
                    return new ResourceNotFoundException("Payment method not found with code: " + code);
                });

        if (Boolean.FALSE.equals(entity.getIsActive())) {
            log.error("Payment method with code {} is inactive", code);
            throw new ResourceNotFoundException("Payment method not found with code: " + code);
        }

        log.debug("Payment method found: {}", entity.getName());
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getAllPaymentMethods() {
        log.debug("Fetching all payment methods");

        List<PaymentMethodEntity> entities = paymentMethodRepository.findAll();
        log.debug("Found {} payment methods (before filtering)", entities.size());

        List<PaymentMethodResponse> responses = entities.stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getIsActive()))
                .map(this::mapToResponse)
                .toList();

        if (responses.isEmpty()) {
            log.error("No active payment methods found");
            throw new ResourceNotFoundException("No active payment methods found");
        }

        log.debug("Returning {} active payment methods", responses.size());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getActivePaymentMethods() {
        log.debug("Fetching all active payment methods");

        List<PaymentMethodEntity> entities = paymentMethodRepository.findAllActive();
        log.debug("Found {} active payment methods", entities.size());

        List<PaymentMethodResponse> responses = entities.stream()
                .map(this::mapToResponse)
                .toList();

        if (responses.isEmpty()) {
            log.error("No active payment methods found");
            throw new ResourceNotFoundException("No active payment methods found");
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsByType(PaymentMethodEntity.PaymentType type) {
        log.debug("Fetching payment methods by type: {}", type);

        List<PaymentMethodEntity> entities = paymentMethodRepository.findByType(type);
        log.debug("Found {} payment methods of type {} (before filtering)", entities.size(), type);

        List<PaymentMethodResponse> responses = entities.stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getIsActive()))
                .map(this::mapToResponse)
                .toList();

        if (responses.isEmpty()) {
            log.error("No active payment methods found for type: {}", type);
            throw new ResourceNotFoundException("No active payment methods found for type: " + type);
        }

        log.debug("Returning {} active payment methods of type {}", responses.size(), type);
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getActivePaymentMethodsByType(PaymentMethodEntity.PaymentType type) {
        log.debug("Fetching active payment methods by type: {}", type);

        List<PaymentMethodEntity> entities = paymentMethodRepository.findAllActiveByType(type);
        log.debug("Found {} active payment methods of type {}", entities.size(), type);

        List<PaymentMethodResponse> responses = entities.stream()
                .map(this::mapToResponse)
                .toList();

        if (responses.isEmpty()) {
            log.error("No active payment methods found for type: {}", type);
            throw new ResourceNotFoundException("No active payment methods found for type: " + type);
        }

        return responses;
    }

    @Override
    public void deletePaymentMethod(String uuid) {
        log.info("Soft deleting payment method with UUID: {}", uuid);

        PaymentMethodEntity entity = paymentMethodRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error(PAYMENT_METHOD_NOT_FOUND_UUID_LOG, uuid);
                    return new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_UUID + uuid);
                });

        entity.setIsActive(false);
        paymentMethodRepository.save(entity);

        log.info("Payment method soft deleted successfully with UUID: {}", uuid);
    }

    @Override
    public PaymentMethodResponse togglePaymentMethodStatus(String uuid) {
        log.info("Toggling payment method status for UUID: {}", uuid);

        PaymentMethodEntity entity = paymentMethodRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error(PAYMENT_METHOD_NOT_FOUND_UUID_LOG, uuid);
                    return new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_UUID + uuid);
                });

        entity.setIsActive(!entity.getIsActive());
        PaymentMethodEntity updatedEntity = paymentMethodRepository.save(entity);

        log.info("Payment method status toggled to {} for UUID: {}", updatedEntity.getIsActive(), uuid);

        return mapToResponse(updatedEntity);
    }

    /**
     * Map entity to response DTO
     */
    private PaymentMethodResponse mapToResponse(PaymentMethodEntity entity) {
        return PaymentMethodResponse.builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .code(entity.getCode())
                .type(entity.getType())
                .isActive(entity.getIsActive())
                .supportsRefund(entity.getSupportsRefund())
                .supportsCallback(entity.getSupportsCallback())
                .isCourierFeeFree(entity.getIsCourierFeeFree())
                .build();
    }
}

package org.psint.beyosclothing.modules.payment.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodConfigResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodConfigEntity;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodConfigRepository;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodConfigService;
import org.psint.beyosclothing.modules.payment.util.JwtTokenEncryption;
import org.psint.beyosclothing.modules.products.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentMethodConfigServiceImpl implements PaymentMethodConfigService {

    private static final String CONFIG_NOT_FOUND_MSG = "Payment method config not found with ID: ";
    private static final String CONFIG_NOT_FOUND_LOG = "Config not found with ID: {}";
    private static final String PAYMENT_METHOD_NOT_FOUND_MSG = "Payment method not found with UUID: ";

    private final PaymentMethodConfigRepository configRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final JwtTokenEncryption jwtEncryption;

    @Override
    public PaymentMethodConfigResponse addConfig(String paymentMethodUuid, CreatePaymentMethodConfigRequest request) {
        log.info("Adding config for payment method UUID: {}, key: {}", paymentMethodUuid, request.getConfigKey());

        String jwtToken = extractJwtToken();

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(paymentMethodUuid)
                .orElseThrow(() -> {
                    log.error("Payment method not found with UUID: {}", paymentMethodUuid);
                    return new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_MSG + paymentMethodUuid);
                });

        if (configRepository.existsByMethodIdAndConfigKey(paymentMethod.getId(), request.getConfigKey())) {
            log.error("Config key {} already exists for payment method {}", request.getConfigKey(), paymentMethodUuid);
            throw new DuplicateResourceException("Config key '" + request.getConfigKey() + "' already exists for this payment method");
        }

        String valueToStore = request.getConfigValue();
        boolean isSecret = Boolean.TRUE.equals(request.getIsSecret());
        if (isSecret) {
            valueToStore = jwtEncryption.encryptWithJwt(request.getConfigValue(), jwtToken);
            log.debug("Encrypted secret config value with JWT for key: {}", request.getConfigKey());
        }

        PaymentMethodConfigEntity entity = PaymentMethodConfigEntity.builder()
                .methodId(paymentMethod.getId())
                .label(request.getLabel())
                .configKey(request.getConfigKey())
                .configValue(valueToStore)
                .isSecret(isSecret)
                .build();

        PaymentMethodConfigEntity savedEntity = configRepository.save(entity);
        log.info("Config added successfully with ID: {} for payment method: {}", savedEntity.getId(), paymentMethodUuid);

        return mapToResponse(savedEntity, request.getConfigValue(), jwtToken);
    }

    @Override
    public PaymentMethodConfigResponse updateConfig(Long configId, UpdatePaymentMethodConfigRequest request) {
        log.info("Updating config with ID: {}", configId);

        // Get JWT token from current request
        String jwtToken = extractJwtToken();

        PaymentMethodConfigEntity entity = configRepository.findById(configId)
                .orElseThrow(() -> {
                    log.error(CONFIG_NOT_FOUND_LOG, configId);
                    return new ResourceNotFoundException(CONFIG_NOT_FOUND_MSG + configId);
                });

        if (request.getLabel() != null) {
            entity.setLabel(request.getLabel());
        }

        if (request.getConfigKey() != null && !request.getConfigKey().equals(entity.getConfigKey())) {
            if (configRepository.existsByMethodIdAndConfigKey(entity.getMethodId(), request.getConfigKey())) {
                log.error("Config key {} already exists for this payment method", request.getConfigKey());
                throw new DuplicateResourceException("Config key '" + request.getConfigKey() + "' already exists for this payment method");
            }
            entity.setConfigKey(request.getConfigKey());
        }

        if (request.getIsSecret() != null) {
            entity.setIsSecret(request.getIsSecret());
        }

        String originalValue = null;
        if (request.getConfigValue() != null) {
            originalValue = request.getConfigValue();
            if (Boolean.TRUE.equals(entity.getIsSecret())) {
                entity.setConfigValue(jwtEncryption.encryptWithJwt(request.getConfigValue(), jwtToken));
                log.debug("Re-encrypted secret config value with JWT for ID: {}", configId);
            } else {
                entity.setConfigValue(request.getConfigValue());
            }
        }

        PaymentMethodConfigEntity updatedEntity = configRepository.save(entity);
        log.info("Config updated successfully with ID: {}", configId);

        return mapToResponse(updatedEntity, originalValue, jwtToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodConfigResponse> getConfigs(String paymentMethodUuid) {
        log.debug("Fetching configs for payment method UUID: {}", paymentMethodUuid);

        String jwtToken = extractJwtToken();

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(paymentMethodUuid)
                .orElseThrow(() -> {
                    log.error("Payment method not found with UUID: {}", paymentMethodUuid);
                    return new ResourceNotFoundException(PAYMENT_METHOD_NOT_FOUND_MSG + paymentMethodUuid);
                });

        List<PaymentMethodConfigEntity> entities = configRepository.findByMethodId(paymentMethod.getId());
        log.debug("Found {} configs for payment method: {}", entities.size(), paymentMethodUuid);

        return entities.stream()
                .map(entity -> mapToResponse(entity, null, jwtToken))
                .toList();
    }

    @Override
    public void deleteConfig(Long configId) {
        log.info("Deleting config with ID: {}", configId);

        if (!configRepository.existsById(configId)) {
            log.error(CONFIG_NOT_FOUND_LOG, configId);
            throw new ResourceNotFoundException(CONFIG_NOT_FOUND_MSG + configId);
        }

        configRepository.deleteById(configId);
        log.info("Config deleted successfully with ID: {}", configId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodConfigResponse getConfigById(Long configId) {
        log.debug("Fetching config by ID: {}", configId);

        String jwtToken = extractJwtToken();

        PaymentMethodConfigEntity entity = configRepository.findById(configId)
                .orElseThrow(() -> {
                    log.error(CONFIG_NOT_FOUND_LOG, configId);
                    return new ResourceNotFoundException(CONFIG_NOT_FOUND_MSG + configId);
                });

        return mapToResponse(entity, null, jwtToken);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDecryptedConfigValue(Long methodId, String configKey) {
        log.debug("Fetching decrypted config value for methodId: {}, key: {}", methodId, configKey);

        String jwtToken = extractJwtToken();

        PaymentMethodConfigEntity entity = configRepository.findByMethodIdAndConfigKey(methodId, configKey)
                .orElseThrow(() -> {
                    log.error("Config not found for methodId: {}, key: {}", methodId, configKey);
                    return new ResourceNotFoundException("Config not found for key: " + configKey);
                });

        if (Boolean.TRUE.equals(entity.getIsSecret())) {
            return jwtEncryption.decryptWithJwt(entity.getConfigValue(), jwtToken);
        }

        return entity.getConfigValue();
    }

    private String extractJwtToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                log.debug("Extracted JWT token for encryption");
                return token;
            }

            log.error("No JWT token found in request");
            throw new RuntimeException("JWT token not found in request");
        } catch (Exception e) {
            log.error("Failed to extract JWT token: {}", e.getMessage());
            throw new RuntimeException("Failed to extract JWT token", e);
        }
    }

    private PaymentMethodConfigResponse mapToResponse(PaymentMethodConfigEntity entity, String originalValue, String jwtToken) {
        String displayValue;

        if (Boolean.TRUE.equals(entity.getIsSecret())) {
            if (originalValue != null) {
                displayValue = originalValue;
            } else {
                try {
                    displayValue = jwtEncryption.decryptWithJwt(entity.getConfigValue(), jwtToken);
                    log.debug("Decrypted config value with JWT for ID: {}", entity.getId());
                } catch (Exception e) {
                    log.warn("Could not decrypt config value for ID: {}, showing masked placeholder", entity.getId());
                    displayValue = jwtEncryption.maskEncryptedValue(entity.getConfigValue());
                }
            }
        } else {
            displayValue = entity.getConfigValue();
        }

        return PaymentMethodConfigResponse.builder()
                .id(entity.getId())
                .methodId(entity.getMethodId())
                .label(entity.getLabel())
                .configKey(entity.getConfigKey())
                .configValue(displayValue)
                .isSecret(entity.getIsSecret())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}


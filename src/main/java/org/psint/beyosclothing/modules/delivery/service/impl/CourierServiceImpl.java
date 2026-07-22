package org.psint.beyosclothing.modules.delivery.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierRateResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.psint.beyosclothing.modules.delivery.repository.CourierRateRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.service.CourierService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourierServiceImpl implements CourierService {

    private static final String COURIER_NOT_FOUND_LOG = "Courier not found with UUID: {}";
    private static final String COURIER_NOT_FOUND_MSG = "Courier not found with UUID: ";
    private static final String COURIER_RATE_NOT_FOUND_LOG = "Courier rate not found with UUID: {}";
    private static final String COURIER_RATE_NOT_FOUND_MSG = "Courier rate not found with UUID: ";


    private final CourierRepository courierRepository;
    private final CourierRateRepository courierRateRepository;

    @Override
    @Transactional
    public CourierResponse createCourier(CreateCourierRequest request) {
        log.debug("Starting courier creation process for code: {}", request.getCode());

        if (courierRepository.existsByCode(request.getCode())) {
            log.warn("Courier code already exists: {}", request.getCode());
            throw new IllegalArgumentException("Courier with code '" + request.getCode() + "' already exists");
        }

        if (courierRepository.existsByName(request.getName())) {
            log.warn("Courier name already exists: {}", request.getName());
            throw new IllegalArgumentException("Courier with name '" + request.getName() + "' already exists");
        }

        Courier courier = Courier.builder()
                .uuid(UUID.randomUUID().toString())
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .apiBaseUrl(request.getApiBaseUrl())
                .apiKey(request.getApiKey())
                .contactPhone(request.getContactPhone())
                .email(request.getEmail())
                .build();

        courier.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);

        Courier savedCourier = courierRepository.save(courier);
        log.info("Courier created successfully with ID: {} and UUID: {}", savedCourier.getId(), savedCourier.getUuid());

        return mapToResponse(savedCourier);
    }

    @Override
    @Transactional
    public CourierResponse updateCourier(String uuid, UpdateCourierRequest request) {
        log.debug("Starting courier update process for UUID: {}", uuid);

        Courier courier = courierRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + uuid);
                });

        if (request.getCode() != null && !request.getCode().isEmpty()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!courier.getCode().equals(newCode) && courierRepository.existsByCodeAndUuidNot(newCode, uuid)) {
                log.warn("Courier code already exists: {}", newCode);
                throw new IllegalArgumentException("Courier with code '" + newCode + "' already exists");
            }
            courier.setCode(newCode);
        }

        if (request.getName() != null && !request.getName().isEmpty()) {
            String newName = request.getName().trim();
            if (!courier.getName().equals(newName) && courierRepository.existsByNameAndUuidNot(newName, uuid)) {
                log.warn("Courier name already exists: {}", newName);
                throw new IllegalArgumentException("Courier with name '" + newName + "' already exists");
            }
            courier.setName(newName);
        }

        updateCourierFields(courier, request);

        Courier updatedCourier = courierRepository.save(courier);
        log.info("Courier updated successfully with UUID: {}", updatedCourier.getUuid());

        return mapToResponse(updatedCourier);
    }

    private void updateCourierFields(Courier courier, UpdateCourierRequest request) {
        if (request.getDescription() != null) {
            courier.setDescription(request.getDescription());
        }

        if (request.getApiBaseUrl() != null) {
            courier.setApiBaseUrl(request.getApiBaseUrl());
        }

        if (request.getApiKey() != null) {
            courier.setApiKey(request.getApiKey());
        }

        if (request.getContactPhone() != null) {
            courier.setContactPhone(request.getContactPhone());
        }

        if (request.getEmail() != null) {
            courier.setEmail(request.getEmail());
        }

        if (request.getIsActive() != null) {
            courier.setIsActive(request.getIsActive());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CourierResponse getCourierByUuid(String uuid) {
        log.debug("Fetching courier with UUID: {}", uuid);

        Courier courier = courierRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + uuid);
                });

        return mapToResponse(courier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierResponse> getAllActiveCouriers() {
        log.debug("Fetching all active couriers");

        List<Courier> couriers = courierRepository.findAllByIsActiveTrue();
        log.info("Found {} active couriers", couriers.size());

        return couriers.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCourier(String uuid) {
        log.debug("Starting soft delete for courier with UUID: {}", uuid);

        Courier courier = courierRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + uuid);
                });

        courier.setIsActive(false);
        courierRepository.save(courier);
        log.info("Courier soft deleted successfully with UUID: {}", uuid);
    }

    @Override
    @Transactional
    public CourierResponse toggleCourierStatus(String uuid) {
        log.debug("Toggling status for courier with UUID: {}", uuid);

        Courier courier = courierRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + uuid);
                });

        Boolean currentStatus = courier.getIsActive();
        courier.setIsActive(!currentStatus);
        Courier updatedCourier = courierRepository.save(courier);

        log.info("Courier status toggled from {} to {} for UUID: {}", currentStatus, !currentStatus, uuid);

        return mapToResponse(updatedCourier);
    }

    private CourierResponse mapToResponse(Courier courier) {
        return CourierResponse.builder()
                .uuid(courier.getUuid())
                .code(courier.getCode())
                .name(courier.getName())
                .description(courier.getDescription())
                .apiBaseUrl(courier.getApiBaseUrl())
                .contactPhone(courier.getContactPhone())
                .email(courier.getEmail())
                .isActive(courier.getIsActive())
                .createdAt(courier.getDateCreated())
                .updatedAt(courier.getDateUpdated())
                .build();
    }

    // Courier Rate Methods------------------------------------------------------

    @Override
    @Transactional
    @CacheEvict(value = "courierRates", allEntries = true)
    public CourierRateResponse createCourierRate(CreateCourierRateRequest request) {
        log.debug("Starting courier rate creation process for courier UUID: {}", request.getCourierUuid());

        // Find the courier
        Courier courier = courierRepository.findByUuid(request.getCourierUuid())
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, request.getCourierUuid());
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + request.getCourierUuid());
                });

        // Create courier rate
        CourierRate courierRate = CourierRate.builder()
                .uuid(UUID.randomUUID().toString())
                .courier(courier)
                .customerType(mapToEntityCustomerType(request.getCustomerType()))
                .paymentMethodId(request.getPaymentMethodId())
                .firstKgPrice(request.getFirstKgPrice())
                .additionalKgPrice(request.getAdditionalKgPrice())
                .weightGranularity(CourierRate.WeightGranularity.valueOf(request.getWeightGranularity()))
                .minCharge(request.getMinCharge())
                .maxCharge(request.getMaxCharge())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();

        courierRate.setIsActive(true);

        CourierRate savedRate = courierRateRepository.save(courierRate);
        log.info("Courier rate created successfully with UUID: {}", savedRate.getUuid());

        return mapToResponse(savedRate);
    }

    @Override
    @Transactional
    @Caching(
            put = @CachePut(value = "courierRate", key = "#uuid"),
            evict = @CacheEvict(value = "courierRates", allEntries = true)
    )
    public CourierRateResponse updateCourierRate(String uuid, UpdateCourierRateRequest request) {
        log.debug("Starting courier rate update process for UUID: {}", uuid);

        CourierRate courierRate = courierRateRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_RATE_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_RATE_NOT_FOUND_MSG + uuid);
                });

        if (request.getCourierUuid() != null) {
            Courier courier = courierRepository.findByUuid(request.getCourierUuid())
                    .orElseThrow(() -> {
                        log.warn(COURIER_NOT_FOUND_LOG, request.getCourierUuid());
                        return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + request.getCourierUuid());
                    });
            courierRate.setCourier(courier);
        }

        updateCourierRateFields(courierRate, request);

        CourierRate updatedRate = courierRateRepository.save(courierRate);
        log.info("Courier rate updated successfully with UUID: {}", updatedRate.getUuid());

        return mapToResponse(updatedRate);
    }

    private void updateCourierRateFields(CourierRate courierRate, UpdateCourierRateRequest request) {
        if (request.getCustomerType() != null) {
            courierRate.setCustomerType(mapToEntityCustomerType(request.getCustomerType()));
        }

        if (request.getPaymentMethodId() != null) {
            courierRate.setPaymentMethodId(request.getPaymentMethodId());
        }

        if (request.getFirstKgPrice() != null) {
            courierRate.setFirstKgPrice(request.getFirstKgPrice());
        }

        if (request.getAdditionalKgPrice() != null) {
            courierRate.setAdditionalKgPrice(request.getAdditionalKgPrice());
        }

        if (request.getWeightGranularity() != null) {
            courierRate.setWeightGranularity(CourierRate.WeightGranularity.valueOf(request.getWeightGranularity()));
        }

        if (request.getMinCharge() != null) {
            courierRate.setMinCharge(request.getMinCharge());
        }

        if (request.getMaxCharge() != null) {
            courierRate.setMaxCharge(request.getMaxCharge());
        }

        if (request.getEffectiveFrom() != null) {
            courierRate.setEffectiveFrom(request.getEffectiveFrom());
        }

        if (request.getEffectiveTo() != null) {
            courierRate.setEffectiveTo(request.getEffectiveTo());
        }

        if (request.getIsActive() != null) {
            courierRate.setIsActive(request.getIsActive());
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "courierRate", key = "#uuid")
    public CourierRateResponse getCourierRateByUuid(String uuid) {
        log.debug("Fetching courier rate with UUID: {}", uuid);

        CourierRate courierRate = courierRateRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_RATE_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_RATE_NOT_FOUND_MSG + uuid);
                });

        return mapToResponse(courierRate);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "courierRates", key = "#courierUuid")
    public List<CourierRateResponse> getCourierRatesByCourier(String courierUuid) {
        log.debug("Fetching courier rates for courier UUID: {}", courierUuid);

        Courier courier = courierRepository.findByUuid(courierUuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_NOT_FOUND_LOG, courierUuid);
                    return new IllegalArgumentException(COURIER_NOT_FOUND_MSG + courierUuid);
                });

        List<CourierRate> rates = courierRateRepository.findByCourier(courier);
        log.info("Found {} courier rates for courier UUID: {}", rates.size(), courierUuid);

        return rates.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "courierRate", key = "#uuid"),
            @CacheEvict(value = "courierRates", allEntries = true)
    })
    public void deleteCourierRate(String uuid) {
        log.debug("Starting soft delete for courier rate with UUID: {}", uuid);

        CourierRate courierRate = courierRateRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_RATE_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_RATE_NOT_FOUND_MSG + uuid);
                });

        courierRate.setIsActive(false);
        courierRateRepository.save(courierRate);
        log.info("Courier rate soft deleted successfully with UUID: {}", uuid);
    }

    @Override
    @Transactional
    @Caching(
            put = @CachePut(value = "courierRate", key = "#uuid"),
            evict = @CacheEvict(value = "courierRates", allEntries = true)
    )
    public CourierRateResponse toggleCourierRateStatus(String uuid) {
        log.debug("Toggling status for courier rate with UUID: {}", uuid);

        CourierRate courierRate = courierRateRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn(COURIER_RATE_NOT_FOUND_LOG, uuid);
                    return new IllegalArgumentException(COURIER_RATE_NOT_FOUND_MSG + uuid);
                });

        Boolean currentStatus = courierRate.getIsActive();
        courierRate.setIsActive(!currentStatus);
        CourierRate updatedRate = courierRateRepository.save(courierRate);

        log.info("Courier rate status toggled from {} to {} for UUID: {}", currentStatus, !currentStatus, uuid);

        return mapToResponse(updatedRate);
    }

    private CourierRateResponse mapToResponse(CourierRate courierRate) {
        CourierResponse courierResponse = CourierResponse.builder()
                .uuid(courierRate.getCourier().getUuid())
                .code(courierRate.getCourier().getCode())
                .name(courierRate.getCourier().getName())
                .description(courierRate.getCourier().getDescription())
                .apiBaseUrl(courierRate.getCourier().getApiBaseUrl())
                .contactPhone(courierRate.getCourier().getContactPhone())
                .email(courierRate.getCourier().getEmail())
                .isActive(courierRate.getCourier().getIsActive())
                .createdAt(courierRate.getCourier().getDateCreated())
                .updatedAt(courierRate.getCourier().getDateUpdated())
                .build();

        return CourierRateResponse.builder()
                .uuid(courierRate.getUuid())
                .couriers(List.of(courierResponse))
                .customerType(mapFromEntityCustomerType(courierRate.getCustomerType()))
                .paymentMethodId(courierRate.getPaymentMethodId())
                .firstKgPrice(courierRate.getFirstKgPrice())
                .additionalKgPrice(courierRate.getAdditionalKgPrice())
                .weightGranularity(courierRate.getWeightGranularity().name())
                .minCharge(courierRate.getMinCharge())
                .maxCharge(courierRate.getMaxCharge())
                .effectiveFrom(courierRate.getEffectiveFrom())
                .effectiveTo(courierRate.getEffectiveTo())
                .isActive(courierRate.getIsActive())
                .createdAt(courierRate.getDateCreated())
                .updatedAt(courierRate.getDateUpdated())
                .build();
    }

    private CourierRate.CustomerType mapToEntityCustomerType(
            org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType dtoType) {
        if (dtoType == null) {
            return CourierRate.CustomerType.BOTH;
        }
        return switch (dtoType) {
            case CUSTOMER -> CourierRate.CustomerType.CUSTOMER;
            case RESELLER -> CourierRate.CustomerType.RESELLER;
            case BOTH -> CourierRate.CustomerType.BOTH;
        };
    }

    private org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType mapFromEntityCustomerType(
            CourierRate.CustomerType entityType) {
        if (entityType == null) {
            return org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType.BOTH;
        }
        return switch (entityType) {
            case CUSTOMER -> org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType.CUSTOMER;
            case RESELLER -> org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType.RESELLER;
            case BOTH -> org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity.CustomerType.BOTH;
        };
    }
}

package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupRequest;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupResponse;
import org.psint.beyosclothing.modules.pos.dto.request.CreatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.request.CustomerSearchRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosCustomerUpdateRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerDetailsResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSearchResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSimpleResponse;
import org.psint.beyosclothing.modules.pos.exception.CustomerServiceUnavailableException;
import org.psint.beyosclothing.modules.pos.service.PosCustomerService;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosCustomerServiceImpl implements PosCustomerService {

    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PosCustomerRepository posCustomerRepository;

    private static final String CUSTOMER_SEARCH_QUEUE = "customer.search.request";
    private static final String CUSTOMER_DETAILS_QUEUE = "customer.details.request";
    private static final String CUSTOMER_SIMPLE_LIST_QUEUE = "customer.simple.list.request";
    private static final String CUSTOMER_UPDATE_QUEUE = "customer.update.request";
    private static final long RPC_TIMEOUT_MS = 500L;
    private static final long CACHE_TTL_MINUTES = 30L;
    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_LIMIT = 50;

    @Override
    public List<PosCustomerSearchResponse> searchCustomers(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty search query provided");
            return Collections.emptyList();
        }

        // Validate and normalize limit
        if (limit < 1) limit = DEFAULT_SEARCH_LIMIT;
        if (limit > MAX_SEARCH_LIMIT) limit = MAX_SEARCH_LIMIT;

        String normalizedQuery = query.trim().toLowerCase();
        String cacheKey = "pos:customer:search:" + normalizedQuery + ":limit:" + limit;

        try {
            @SuppressWarnings("unchecked")
            List<PosCustomerSearchResponse> cached = (List<PosCustomerSearchResponse>) redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.debug("Cache hit for customer search: query='{}', limit={}", query, limit);
                return cached;
            }

            log.debug("Cache miss for customer search: query='{}', limit={}", query, limit);
        } catch (Exception e) {
            log.warn("Failed to read customer search cache: {}", e.getMessage());
        }

        List<PosCustomerSearchResponse> results = searchCustomersViaRPC(query, limit);

        try {
            redisTemplate.opsForValue().set(cacheKey, results, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached customer search results for query: '{}', limit={}, count={}", query, limit, results.size());
        } catch (Exception e) {
            log.warn("Failed to cache customer search results: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public PosCustomerDetailsResponse getCustomerDetails(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }

        String cacheKey = "pos:customer:" + customerId;

        try {
            PosCustomerDetailsResponse cached = (PosCustomerDetailsResponse) redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.debug("Cache hit for customer details: {}", customerId);
                return cached;
            }

            log.debug("Cache miss for customer details: {}", customerId);
        } catch (Exception e) {
            log.warn("Failed to read customer details cache: {}", e.getMessage());
        }

        PosCustomerDetailsResponse details = getCustomerDetailsViaRPC(customerId);

        try {
            redisTemplate.opsForValue().set(cacheKey, details, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached customer details for ID: {}", customerId);
        } catch (Exception e) {
            log.warn("Failed to cache customer details: {}", e.getMessage());
        }

        return details;
    }

    @Override
    @Transactional
    public PosCustomerDetailsResponse createPosCustomer(CreatePosCustomerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        // Basic normalization
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;

        // Prevent duplicate phone entry
        if (phone != null) {
            posCustomerRepository.findByPhone(phone).ifPresent(existing -> {
                throw new IllegalArgumentException("Customer with this phone already exists");
            });
        }

        String uuid = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        PosCustomerEntity entity = PosCustomerEntity.builder()
                .uuid(uuid)
                .fullName(request.getFullName())
                .phone(phone)
                .address(request.getAddress())
                .city(request.getCity())
                .province(request.getProvince())
                .district(request.getDistrict())
                .zipCode(request.getZipCode())
                .dateCreated(now)
                .dateUpdated(now)
                .isActive(true)
                .build();

        PosCustomerEntity saved = posCustomerRepository.save(entity);

        // Map to response

        return PosCustomerDetailsResponse.builder()
                .id(saved.getId())
                .uuid(saved.getUuid())
                .userId(null)
                .fullName(saved.getFullName())
                .phone(saved.getPhone())
                .email(null)
                .isActive(saved.getIsActive())
                .loyaltyPoints(0)
                .dateCreated(saved.getDateCreated())
                .addresses(Collections.emptyList())
                .build();
    }

    @Override
    @Transactional
    public PosCustomerDetailsResponse updatePosCustomer(Long customerId, UpdatePosCustomerRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if ("ONLINE".equalsIgnoreCase(request.getSource())) {
            return updateOnlineCustomerViaRPC(customerId, request);
        }

        return updatePosCustomerLocal(customerId, request);
    }

    private PosCustomerDetailsResponse updatePosCustomerLocal(Long customerId, UpdatePosCustomerRequest request) {
        PosCustomerEntity entity = posCustomerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + customerId));

        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            if (!phone.equals(entity.getPhone())) {
                posCustomerRepository.findByPhone(phone).ifPresent(existing -> {
                    throw new IllegalArgumentException("Customer with this phone already exists");
                });
            }
            entity.setPhone(phone);
        }

        if (request.getFullName() != null) entity.setFullName(request.getFullName());
        if (request.getAddress() != null)  entity.setAddress(request.getAddress());
        if (request.getCity() != null)     entity.setCity(request.getCity());
        if (request.getProvince() != null) entity.setProvince(request.getProvince());
        if (request.getDistrict() != null) entity.setDistrict(request.getDistrict());
        if (request.getZipCode() != null)  entity.setZipCode(request.getZipCode());
        entity.setDateUpdated(LocalDateTime.now());

        PosCustomerEntity saved = posCustomerRepository.save(entity);

        try {
            redisTemplate.delete("pos:customer:" + customerId);
        } catch (Exception e) {
            log.warn("Failed to evict customer cache {}: {}", customerId, e.getMessage());
        }

        return PosCustomerDetailsResponse.builder()
                .id(saved.getId())
                .uuid(saved.getUuid())
                .userId(null)
                .fullName(saved.getFullName())
                .phone(saved.getPhone())
                .email(null)
                .isActive(saved.getIsActive())
                .loyaltyPoints(0)
                .dateCreated(saved.getDateCreated())
                .addresses(Collections.emptyList())
                .build();
    }

    private PosCustomerDetailsResponse updateOnlineCustomerViaRPC(Long customerId, UpdatePosCustomerRequest request) {
        try {
            PosCustomerUpdateRequest rpcRequest = PosCustomerUpdateRequest.builder()
                    .customerId(customerId)
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .province(request.getProvince())
                    .district(request.getDistrict())
                    .zipCode(request.getZipCode())
                    .build();

            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            PosCustomerDetailsResponse response = (PosCustomerDetailsResponse) rabbitTemplate.convertSendAndReceive(
                    CUSTOMER_UPDATE_QUEUE,
                    rpcRequest
            );

            if (response == null) {
                log.warn("ONLINE customer update RPC returned null for ID: {}", customerId);
                throw new CustomerServiceUnavailableException("Customer service unavailable for update of ID: " + customerId);
            }

            try {
                redisTemplate.delete("pos:customer:" + customerId);
            } catch (Exception e) {
                log.warn("Failed to evict customer cache for ID {}: {}", customerId, e.getMessage());
            }

            log.info("ONLINE customer updated successfully via RPC for ID: {}", customerId);
            return response;

        } catch (CustomerServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("ONLINE customer update RPC failed for ID {}: {}", customerId, e.getMessage());
            throw new CustomerServiceUnavailableException("Failed to update ONLINE customer ID: " + customerId);
        }
    }

    @Override
    public List<PosCustomerSimpleResponse> getAllSimpleCustomers() {
        List<PosCustomerSimpleResponse> allCustomers = new ArrayList<>();

        // Step 1: Get POS customers from local database
        try {
            List<PosCustomerEntity> posCustomers = posCustomerRepository.findAll();
            List<PosCustomerSimpleResponse> posCustomerResponses = posCustomers.stream()
                    .map(this::mapToSimpleResponse)
                    .toList();

            allCustomers.addAll(posCustomerResponses);
            log.info("Retrieved {} customers from POS module", posCustomerResponses.size());
        } catch (Exception e) {
            log.error("Failed to retrieve POS customers: {}", e.getMessage(), e);
        }

        // Step 2: Get ONLINE customers from customer module via RabbitMQ
        try {
            String requestId = UUID.randomUUID().toString();
            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            @SuppressWarnings("unchecked")
            List<PosCustomerSimpleResponse> onlineCustomers = (List<PosCustomerSimpleResponse>)
                    rabbitTemplate.convertSendAndReceive(CUSTOMER_SIMPLE_LIST_QUEUE, requestId);

            if (onlineCustomers != null && !onlineCustomers.isEmpty()) {
                allCustomers.addAll(onlineCustomers);
                log.info("Retrieved {} customers from ONLINE module", onlineCustomers.size());
            } else {
                log.warn("No ONLINE customers returned from customer module");
            }
        } catch (Exception e) {
            log.error("Failed to retrieve ONLINE customers via RabbitMQ: {}", e.getMessage(), e);
        }

        log.info("Total customers retrieved: {} (POS + ONLINE)", allCustomers.size());
        return allCustomers;
    }

    private List<PosCustomerSearchResponse> searchCustomersViaRPC(String query, int limit) {
        try {
            CustomerSearchRequest request = CustomerSearchRequest.builder()
                    .query(query)
                    .limit(limit)
                    .build();

            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            @SuppressWarnings("unchecked")
            List<PosCustomerSearchResponse> response = (List<PosCustomerSearchResponse>) rabbitTemplate.convertSendAndReceive(
                    CUSTOMER_SEARCH_QUEUE,
                    request
            );

            if (response == null) {
                log.warn("Customer search RPC returned null for query: {}", query);
                return tryFallbackSearch(query, limit);
            }

            log.info("Customer search completed - query: {}, limit: {}, results: {}", query, limit, response.size());
            return response;

        } catch (Exception e) {
            log.error("Customer search RPC failed for query: {}: {}", query, e.getMessage());
            return tryFallbackSearch(query, limit);
        }
    }

    private PosCustomerDetailsResponse getCustomerDetailsViaRPC(Long customerId) {
        try {
            CustomerDetailsLookupRequest request = CustomerDetailsLookupRequest.builder()
                    .customerIds(java.util.Collections.singletonList(customerId))
                    .includeAddresses(true)
                    .build();

            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            CustomerDetailsLookupResponse response = (CustomerDetailsLookupResponse) rabbitTemplate.convertSendAndReceive(
                    CUSTOMER_DETAILS_QUEUE,
                    request
            );

            if (response == null) {
                log.warn("Customer details RPC returned null for ID: {}", customerId);
                return tryFallbackDetails(customerId);
            }

            log.info("Customer details retrieved for ID: {}", customerId);
            return mapToDetailsResponse(response);

        } catch (Exception e) {
            log.error("Customer details RPC failed for ID: {}: {}", customerId, e.getMessage());
            return tryFallbackDetails(customerId);
        }
    }

    private List<PosCustomerSearchResponse> tryFallbackSearch(String query, int limit) {
        String normalizedQuery = query.trim().toLowerCase();
        String cacheKey = "pos:customer:search:" + normalizedQuery + ":limit:" + limit;

        try {
            @SuppressWarnings("unchecked")
            List<PosCustomerSearchResponse> cached = (List<PosCustomerSearchResponse>) redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.info("Using cached fallback data for customer search: query='{}', limit={}", query, limit);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Fallback cache read failed: {}", e.getMessage());
        }

        log.warn("No fallback data available for customer search: query='{}', limit={}", query, limit);
        return Collections.emptyList();
    }

    private PosCustomerDetailsResponse tryFallbackDetails(Long customerId) {
        String cacheKey = "pos:customer:" + customerId;

        try {
            PosCustomerDetailsResponse cached = (PosCustomerDetailsResponse) redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.info("Using cached fallback data for customer ID: {}", customerId);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Fallback cache read failed: {}", e.getMessage());
        }

        log.warn("No fallback data available for customer ID: {}", customerId);
        throw new CustomerServiceUnavailableException("Customer service unavailable and no cached data found for ID: " + customerId);
    }

    private PosCustomerDetailsResponse mapToDetailsResponse(CustomerDetailsLookupResponse source) {
        if (source == null) {
            return null;
        }

        return PosCustomerDetailsResponse.builder()
                .id(source.getId())
                .uuid(source.getUuid())
                .userId(source.getUserId())
                .fullName(source.getFullName())
                .phone(source.getPhone())
                .email(source.getEmail())
                .isActive(source.getIsActive())
                .loyaltyPoints(0)
                .dateCreated(source.getDateCreated())
                .addresses(source.getAddresses() != null ?
                        source.getAddresses().stream()
                                .map(this::mapToAddressResponse)
                                .collect(Collectors.toList()) :
                        Collections.emptyList())
                .build();
    }

    private PosCustomerDetailsResponse.CustomerAddress mapToAddressResponse(CustomerDetailsLookupResponse.Address source) {
        return PosCustomerDetailsResponse.CustomerAddress.builder()
                .id(source.getId())
                .addressLine1(source.getAddressLine1())
                .addressLine2(source.getAddressLine2())
                .city(source.getCity())
                .district(source.getDistrict())
                .country(source.getCountry())
                .postalCode(source.getPostalCode())
                .isDefault(source.getIsDefault())
                .build();
    }

    private PosCustomerSimpleResponse mapToSimpleResponse(PosCustomerEntity customer) {
        PosCustomerSimpleResponse.CustomerAddress address = PosCustomerSimpleResponse.CustomerAddress.builder()
                .addressLine1(customer.getAddress())
                .city(customer.getCity())
                .province(customer.getProvince())
                .district(customer.getDistrict())
                .postalCode(customer.getZipCode())
                .isDefault(true)
                .build();

        return PosCustomerSimpleResponse.builder()
                .id(customer.getId())
                .uuid(customer.getUuid())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .city(customer.getCity())
                .province(customer.getProvince())
                .district(customer.getDistrict())
                .zipCode(customer.getZipCode())
                .addresses(Collections.singletonList(address))
                .source("POS")
                .build();
    }
}

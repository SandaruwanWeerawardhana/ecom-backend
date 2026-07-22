package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.service.PosCustomerLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * POS Customer Lookup Service Implementation
 * Uses RabbitMQ to query Customer module for customer information
 * Decouples POS module from direct Customer module dependency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosCustomerLookupServiceImpl implements PosCustomerLookupService {

    // Constants for request/response keys
    private static final String REQUEST_ID = "requestId";
    private static final String REQUEST_TYPE = "requestType";
    private static final String CUSTOMER_UUID = "customerUuid";
    private static final String CUSTOMER_ID = "customerId";
    private static final String SUCCESS = "success";
    private static final String ERROR_MESSAGE = "errorMessage";

    private static final String TYPE_UUID_TO_ID = "UUID_TO_ID";
    private static final String TYPE_ID_TO_UUID = "ID_TO_UUID";
    private static final String ROUTING_KEY = "pos.customer.lookup.request";
    private static final long TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Override
    public Long getCustomerIdByUuid(String customerUuid) {
        if (customerUuid == null || customerUuid.trim().isEmpty()) {
            return null;
        }

        log.debug("Requesting customer ID for UUID: {} via RabbitMQ", customerUuid);

        Map<String, Object> request = buildRequest(TYPE_UUID_TO_ID);
        request.put(CUSTOMER_UUID, customerUuid);

        Map<String, Object> response = sendRequest(request);

        if (response != null && Boolean.TRUE.equals(response.get(SUCCESS))) {
            Object customerIdObj = response.get(CUSTOMER_ID);
            if (customerIdObj instanceof Number) {
                Long customerId = ((Number) customerIdObj).longValue();
                log.debug("Customer UUID {} resolved to ID {}", customerUuid, customerId);
                return customerId;
            }
        }

        log.warn("Customer not found for UUID: {}", customerUuid);
        return null;
    }

    @Override
    public String getCustomerUuidById(Long customerId) {
        if (customerId == null) {
            return null;
        }

        log.debug("Requesting customer UUID for ID: {} via RabbitMQ", customerId);

        Map<String, Object> request = buildRequest(TYPE_ID_TO_UUID);
        request.put(CUSTOMER_ID, customerId);

        Map<String, Object> response = sendRequest(request);

        if (response != null && Boolean.TRUE.equals(response.get(SUCCESS))) {
            Object customerUuidObj = response.get(CUSTOMER_UUID);
            if (customerUuidObj instanceof String) {
                String customerUuid = (String) customerUuidObj;
                log.debug("Customer ID {} resolved to UUID {}", customerId, customerUuid);
                return customerUuid;
            }
        }

        log.warn("Customer not found for ID: {}", customerId);
        return null;
    }

    /**
     * Build RabbitMQ request with common fields
     */
    private Map<String, Object> buildRequest(String requestType) {
        Map<String, Object> request = new HashMap<>();
        request.put(REQUEST_ID, UUID.randomUUID().toString());
        request.put(REQUEST_TYPE, requestType);
        return request;
    }

    /**
     * Send request via RabbitMQ and handle response
     */
    private Map<String, Object> sendRequest(Map<String, Object> request) {
        try {
            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    customerExchange,
                    ROUTING_KEY,
                    request
            );

            log.debug("Received customer lookup response: {}", response);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;

                if (!Boolean.TRUE.equals(responseMap.get(SUCCESS))) {
                    String errorMessage = (String) responseMap.get(ERROR_MESSAGE);
                    log.warn("Customer lookup failed: {}", errorMessage);
                }

                return responseMap;
            }

            return null;

        } catch (Exception e) {
            log.error("Error during customer lookup via RabbitMQ", e);
            return null;
        }
    }
}



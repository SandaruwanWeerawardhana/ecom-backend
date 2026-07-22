package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for POS Customer Lookup Requests
 * Handles customer UUID ↔ ID conversion requests from POS module
 * Decouples POS module from direct Customer module dependency
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosCustomerLookupConsumer {

    // Constants for request/response keys
    private static final String REQUEST_ID = "requestId";
    private static final String REQUEST_TYPE = "requestType";
    private static final String CUSTOMER_UUID = "customerUuid";
    private static final String CUSTOMER_ID = "customerId";
    private static final String SUCCESS = "success";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String EMAIL = "email";
    private static final String PHONE_NUMBER = "phoneNumber";
    private static final String IS_ACTIVE = "isActive";

    private static final String TYPE_UUID_TO_ID = "UUID_TO_ID";
    private static final String TYPE_ID_TO_UUID = "ID_TO_UUID";

    private final CustomerRepository customerRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-customer-lookup-request:pos.customer.lookup.request}")
    public Map<String, Object> handleCustomerLookupRequest(Map<String, Object> request) {
        String requestId = (String) request.get(REQUEST_ID);
        String requestType = (String) request.get(REQUEST_TYPE);

        log.debug("🔍 [CUSTOMER LOOKUP] Received request: type={}, id={}", requestType, requestId);

        try {
            if (TYPE_UUID_TO_ID.equals(requestType)) {
                return handleUuidToIdRequest(requestId, request);
            } else if (TYPE_ID_TO_UUID.equals(requestType)) {
                return handleIdToUuidRequest(requestId, request);
            } else {
                log.warn("Unknown request type: {}", requestType);
                return buildErrorResponse(requestId, "Unknown request type: " + requestType);
            }
        } catch (Exception e) {
            log.error("❌ [CUSTOMER LOOKUP] Error processing request", e);
            return buildErrorResponse(requestId, "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Handle UUID to ID conversion request
     */
    private Map<String, Object> handleUuidToIdRequest(String requestId, Map<String, Object> request) {
        String customerUuid = (String) request.get(CUSTOMER_UUID);

        if (customerUuid == null || customerUuid.trim().isEmpty()) {
            log.warn("Missing customerUuid in request");
            return buildErrorResponse(requestId, "Missing customerUuid");
        }

        log.debug("Looking up customer by UUID: {}", customerUuid);

        Optional<Customer> customerOpt = customerRepository.findByUuid(customerUuid);

        if (customerOpt.isEmpty()) {
            log.warn("Customer not found for UUID: {}", customerUuid);
            return buildErrorResponse(requestId, "Customer not found");
        }

        Customer customer = customerOpt.get();
        log.info("✅ [CUSTOMER LOOKUP] UUID {} → ID {}", customerUuid, customer.getId());

        return buildSuccessResponse(requestId, customer);
    }

    /**
     * Handle ID to UUID conversion request
     */
    private Map<String, Object> handleIdToUuidRequest(String requestId, Map<String, Object> request) {
        Object customerIdObj = request.get(CUSTOMER_ID);

        if (customerIdObj == null) {
            log.warn("Missing customerId in request");
            return buildErrorResponse(requestId, "Missing customerId");
        }

        Long customerId = ((Number) customerIdObj).longValue();
        log.debug("Looking up customer by ID: {}", customerId);

        Optional<Customer> customerOpt = customerRepository.findById(customerId);

        if (customerOpt.isEmpty()) {
            log.warn("Customer not found for ID: {}", customerId);
            return buildErrorResponse(requestId, "Customer not found");
        }

        Customer customer = customerOpt.get();
        log.info("✅ [CUSTOMER LOOKUP] ID {} → UUID {}", customerId, customer.getUuid());

        return buildSuccessResponse(requestId, customer);
    }

    /**
     * Build success response with customer data (reusable for both request types)
     */
    private Map<String, Object> buildSuccessResponse(String requestId, Customer customer) {
        Map<String, Object> response = new HashMap<>();
        response.put(REQUEST_ID, requestId);
        response.put(SUCCESS, true);
        response.put(CUSTOMER_ID, customer.getId());
        response.put(CUSTOMER_UUID, customer.getUuid());
        response.put(EMAIL, customer.getEmail());
        response.put(PHONE_NUMBER, customer.getPhoneNumber());
        response.put(IS_ACTIVE, customer.getIsActive());
        return response;
    }

    /**
     * Build error response (reusable for all error cases)
     */
    private Map<String, Object> buildErrorResponse(String requestId, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put(REQUEST_ID, requestId);
        response.put(SUCCESS, false);
        response.put(ERROR_MESSAGE, errorMessage);
        return response;
    }
}


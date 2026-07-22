package org.psint.beyosclothing.modules.orders.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderWithCourierRequest;
import org.psint.beyosclothing.modules.orders.dto.request.AddPickupRequestDto;
import org.psint.beyosclothing.modules.orders.dto.response.PlaceOrderWithCourierResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AddPickupRequestResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.service.AdminOrderCourierService;
import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderShippingAddressRepository;
import org.psint.beyosclothing.modules.customers.entity.Address;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.AddressRepository;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service Implementation for placing orders with courier
 * Handles the complete flow of placing an order with Koombiyo courier service
 *
 * ARCHITECTURE NOTE:
 * - Order module ONLY uses order-related repositories (OrderRepository, OrderShippingAddressRepository)
 * - Delivery module operations (Shipment, Courier) are accessed ONLY via RabbitMQ
 * - No cross-module dependencies on delivery module entities or repositories
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminOrderCourierServiceImpl implements AdminOrderCourierService {

    private final OrderRepository orderRepository;
    private final OrderShippingAddressRepository orderShippingAddressRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final PosCustomerRepository posCustomerRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.delivery:beyos.exchange.delivery}")
    private String deliveryExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    private static final String KOOMBIYO_BASE_URL = "https://application.koombiyodelivery.lk/api";
    private static final String ADD_ORDERS_PATH = "Addorders/users";
    private static final int DELIVERY_DAYS = 4; // Expected delivery in 4 days
    private static final long RABBITMQ_TIMEOUT_SECONDS = 5;

    @Override
    @Transactional("orderTransactionManager")
    public PlaceOrderWithCourierResponse placeOrderWithCourier(PlaceOrderWithCourierRequest request) {
        log.info("=== PLACE ORDER WITH COURIER - START ===");
        log.info("Order UUID: {}, Waybill ID: {}", request.getOrderUuid(), request.getWayBillId());

        try {
            // 1. Fetch order and validate (Order module only)
            OrderEntity order = orderRepository.findByUuid(request.getOrderUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with UUID: " + request.getOrderUuid()));

            log.info("✅ Order found: {} (ID: {}, Total: {})", order.getOrderNumber(), order.getId(), order.getTotal());

            // 2. Fetch shipping address - use the order's stored address, otherwise fall back to
            //    the customer's own address (ONLINE -> Customer, POS -> PosCustomer)
            OrderShippingAddressEntity shippingAddress = orderShippingAddressRepository.findByOrderId(order.getId())
                    .orElseGet(() -> resolveShippingAddressFromCustomer(order));

            if (shippingAddress == null) {
                throw new ResourceNotFoundException("Shipping address not found for order: " + order.getOrderNumber());
            }

            log.info("✅ Shipping address found: {} - {}, {}",
                    shippingAddress.getFullName(), shippingAddress.getAddressLine1(), shippingAddress.getCity());

            // 3. Get active courier (via RabbitMQ - NO DIRECT DELIVERY MODULE ACCESS)
            Map<String, Object> courierResponse = getActiveCourierViaRabbitMQ();

            if (courierResponse == null || !(Boolean) courierResponse.get("success")) {
                throw new RuntimeException("Failed to fetch active courier - " +
                        (courierResponse != null ? courierResponse.get("error") : "Unknown error"));
            }

            String courierApiKey = (String) courierResponse.get("apiKey");
            String courierApiBaseUrl = (String) courierResponse.get("apiBaseUrl");
            String courierName = (String) courierResponse.get("name");
            Long courierId = ((Number) courierResponse.get("id")).longValue();

            log.info("✅ Active courier: {} - API Key: {}", courierName, courierApiKey.substring(0, Math.min(10, courierApiKey.length())) + "***");

            // 4. Create/Update Shipment via RabbitMQ (NO DIRECT DELIVERY MODULE ACCESS)
            Map<String, Object> shipmentData = createOrGetShipmentViaRabbitMQ(order, courierId, courierName,shippingAddress);

            if (shipmentData == null || !(Boolean) shipmentData.get("success")) {
                throw new RuntimeException("Failed to create/get shipment - " +
                        (shipmentData != null ? shipmentData.get("error") : "Unknown error"));
            }

            String shipmentUuid = (String) shipmentData.get("shipmentUuid");
            log.info("✅ Shipment created/updated: {} (ID: {})", shipmentUuid, shipmentData.get("shipmentId"));

            // 5. Call Koombiyo API to add order
            Map<String, Object> koombiyoRequest = buildKoombiyoRequest(
                    request.getWayBillId(),
                    order,
                    shippingAddress,
                    request.getSpecialNotes(),
                    order.getTotal(),
                    courierApiKey
            );

            log.info("Calling Koombiyo API: POST {}{}", courierApiBaseUrl, ADD_ORDERS_PATH);
            Map<String, Object> koombiyoApiResponse = callKoombiyoAddOrdersAPI(
                    courierApiBaseUrl,
                    courierApiKey,
                    koombiyoRequest,
                    courierId
            );

            log.info("✅ Koombiyo API response received: {}", koombiyoApiResponse);

            // 6. Update order status via RabbitMQ event
            publishOrderPlacedWithCourierEvent(order, request.getWayBillId(), courierName);

            // 7. Update shipmenpublishShipmentPlacedWithCourierEventt status via RabbitMQ event
            publishShipmentPlacedWithCourierEvent(shipmentUuid, request.getWayBillId(), courierName, koombiyoApiResponse);

            // 8. Create payment transaction via RabbitMQ event
            publishPaymentTransactionCreateEvent(order, order.getTotal());

            // 9. Build and return response
            LocalDateTime expectedDelivery = LocalDateTime.now().plus(DELIVERY_DAYS, ChronoUnit.DAYS);

            PlaceOrderWithCourierResponse response = PlaceOrderWithCourierResponse.builder()
                    .orderUuid(order.getUuid())
                    .orderNumber(order.getOrderNumber())
                    .shipmentUuid(shipmentUuid)
                    .wayBillId(request.getWayBillId())
                    .courierName(courierName)
                    .orderStatus(OrderEntity.OrderStatus.PROCESSING.toString())
                    .shipmentStatus("BOOKED")
                    .expectedDeliveryDate(expectedDelivery)
                    .codAmount(request.getCodAmount())
                    .courierApiMessage((String) koombiyoApiResponse.getOrDefault("message", "Order successfully added to Koombiyo"))
                    .bookedAt(LocalDateTime.now())
                    .success(true)
                    .errorMessage(null)
                    .build();

            log.info("=== PLACE ORDER WITH COURIER - SUCCESS ===");
            return response;

        } catch (Exception e) {
            log.error("❌ Error in place order with courier: {}", e.getMessage(), e);

            return PlaceOrderWithCourierResponse.builder()
                    .orderUuid(request.getOrderUuid())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Builds a shipping address from the order's customer record when the order has no stored
     * shipping address. The order source decides which customer table is read: POS orders use the
     * PosCustomer record, ONLINE orders use the online Customer plus its default address.
     * Returns null when the order has no customer, or the customer/address cannot be found.
     */
    private OrderShippingAddressEntity resolveShippingAddressFromCustomer(OrderEntity order) {
        Long customerId = order.getCustomerId();
        if (customerId == null) {
            log.warn("Order {} has no customerId - cannot resolve shipping address from customer",
                    order.getOrderNumber());
            return null;
        }

        if (order.getSource() == OrderEntity.OrderSource.POS) {
            return posCustomerRepository.findById(customerId)
                    .map(posCustomer -> buildShippingAddressFromPosCustomer(order.getId(), posCustomer))
                    .orElse(null);
        }

        return customerRepository.findById(customerId)
                .map(customer -> buildShippingAddressFromCustomer(order.getId(), customer))
                .orElse(null);
    }

    private OrderShippingAddressEntity buildShippingAddressFromPosCustomer(Long orderId, PosCustomerEntity posCustomer) {
        return OrderShippingAddressEntity.builder()
                .orderId(orderId)
                .fullName(posCustomer.getFullName())
                .phone(posCustomer.getPhone())
                .addressLine1(posCustomer.getAddress())
                .city(posCustomer.getCity())
                .province(posCustomer.getProvince())
                .postalCode(posCustomer.getZipCode())
                .country("Sri Lanka")
                .build();
    }

    private OrderShippingAddressEntity buildShippingAddressFromCustomer(Long orderId, Customer customer) {
        Address address = addressRepository.findDefaultAddressByCustomerId(customer.getId())
                .or(() -> addressRepository.findByCustomerId(customer.getId()).stream().findFirst())
                .orElse(null);

        if (address == null) {
            log.warn("Customer {} has no saved address - cannot resolve shipping address", customer.getId());
            return null;
        }

        return OrderShippingAddressEntity.builder()
                .orderId(orderId)
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    /**
     * Get active courier via RabbitMQ lookup
     * Communicates with Delivery module using Map-based RPC
     */
    private Map<String, Object> getActiveCourierViaRabbitMQ() {
        try {
            log.info("Fetching active courier via RabbitMQ...");

            Map<String, Object> request = new HashMap<>();
            request.put("requestType", "GET_ACTIVE_COURIER");

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(RABBITMQ_TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "active.courier.lookup.request",
                    request
            );

            if (response instanceof Map) {
                return (Map<String, Object>) response;
            }

            log.error("Invalid response type from RabbitMQ: {}", response != null ? response.getClass() : "null");
            return null;
        } catch (Exception e) {
            log.error("Error fetching active courier from RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create or get shipment via RabbitMQ
     * Communicates with Delivery module using Map-based RPC
     */
    private Map<String, Object> createOrGetShipmentViaRabbitMQ(OrderEntity order, Long courierId, String courierName,OrderShippingAddressEntity shippingAddress) {
        try {
            log.info("Creating/Getting shipment via RabbitMQ...");

            Map<String, Object> request = new HashMap<>();
            request.put("requestType", "CREATE_OR_GET_SHIPMENT");
            request.put("orderId", order.getId());
            request.put("wayBillId", order.getWayBillId());
            request.put("courierId", courierId);
            request.put("courierName", courierName);
            request.put("shipmentWeight", order.getShippingWeight());
            request.put("shippingCost", order.getShippingCost());
            request.put("receiverPhone", shippingAddress.getPhone());

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(RABBITMQ_TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "shipment.create.or.get.request",
                    request
            );

            if (response instanceof Map) {
                return (Map<String, Object>) response;
            }

            log.error("Invalid response type from RabbitMQ: {}", response != null ? response.getClass() : "null");
            return null;
        } catch (Exception e) {
            log.error("Error creating/getting shipment from RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build Koombiyo API request payload
     */
    private Map<String, Object> buildKoombiyoRequest(
            String wayBillId,
            OrderEntity order,
            OrderShippingAddressEntity address,
            String specialNotes,
            BigDecimal codAmount,
            String apiKey
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("apikey", apiKey);
        request.put("orderWaybillid", wayBillId);
        request.put("orderNo", order.getOrderNumber());
        request.put("receiverName", address.getFullName());
        request.put("receiverStreet", address.getAddressLine1());
        request.put("receiverDistrict", address.getDistrictId() != null ? address.getDistrictId() : 1);
        request.put("receiverCity", address.getCityId() != null ? address.getCityId() : 1);
        request.put("receiverPhone", address.getPhone());
        request.put("description", "Order: " + order.getNotes());
        request.put("spclNote", specialNotes != null ? specialNotes : "");
        request.put("getCod", codAmount != null ? codAmount.intValue() : order.getTotal().intValue());

        return request;
    }

    /**
     * Call Koombiyo Add Orders API
     */
    private Map<String, Object> callKoombiyoAddOrdersAPI(
            String apiBaseUrl,
            String apiKey,
            Map<String, Object> requestPayload,
            Long courierId
    ) {
        try {
            String endpoint = apiBaseUrl + ADD_ORDERS_PATH;
            String requestBody = buildFormUrlEncodedBody(requestPayload);

            log.info("Calling Koombiyo API: {}", endpoint);
            log.debug("Request payload: {}", requestBody);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);

            org.springframework.http.ResponseEntity<String> apiResponse = restTemplate.postForEntity(
                    endpoint,
                    entity,
                    String.class
            );

            int httpStatus = apiResponse.getStatusCode().value();
            String responseBody = apiResponse.getBody();

            log.info("Koombiyo API Response [HTTP {}]: {}", httpStatus, responseBody);

            // Log API call via RabbitMQ (Delivery module responsibility)
            logCourierApiCallViaRabbitMQ(courierId, endpoint, requestBody, responseBody, httpStatus);

            // Parse response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", httpStatus == 200);
            responseMap.put("httpStatus", httpStatus);
            responseMap.put("message", responseBody);
            responseMap.put("responseBody", responseBody);

            // Extract pick_id from Koombiyo response if available
            try {
                Map<String, Object> parsedResponse = objectMapper.readValue(responseBody, Map.class);
                if (parsedResponse.containsKey("pick_id")) {
                    log.info("Koombiyo response contains pick_id: {}", parsedResponse.get("pick_id"));
                    responseMap.put("pickId", parsedResponse.get("pick_id"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse pick_id from response: {}", e.getMessage());
            }

            return responseMap;

        } catch (HttpStatusCodeException ex) {
            int httpStatus = ex.getStatusCode().value();
            String errorBody = ex.getResponseBodyAsString();

            log.error("Koombiyo API Error [HTTP {}]: {}", httpStatus, errorBody);

            // Log failed API call via RabbitMQ
            try {
                logCourierApiCallViaRabbitMQ(courierId, apiBaseUrl + ADD_ORDERS_PATH,
                        objectMapper.writeValueAsString(requestPayload), errorBody, httpStatus);
            } catch (Exception logEx) {
                log.error("Error logging failed API call: {}", logEx.getMessage());
            }

            throw new RuntimeException("Koombiyo API call failed [HTTP " + httpStatus + "]: " + errorBody);
        } catch (Exception e) {
            log.error("Unexpected error calling Koombiyo API: {}", e.getMessage(), e);

            // Log error via RabbitMQ
            String requestPayloadStr;
            try {
                requestPayloadStr = objectMapper.writeValueAsString(requestPayload);
            } catch (Exception ex) {
                requestPayloadStr = requestPayload.toString();
            }

            try {
                logCourierApiCallViaRabbitMQ(courierId, apiBaseUrl + ADD_ORDERS_PATH,
                        requestPayloadStr, e.getMessage(), 0);
            } catch (Exception logEx) {
                log.error("Error logging API call exception: {}", logEx.getMessage());
            }

            throw new RuntimeException("Error calling Koombiyo API: " + e.getMessage());
        }
    }

    /**
     * Log courier API call via RabbitMQ (asynchronous event to Delivery module)
     */
    private void logCourierApiCallViaRabbitMQ(Long courierId, String endpoint, String requestPayload, String responsePayload, int httpStatus) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("courierId", courierId);
            event.put("endpoint", endpoint);
            event.put("requestPayload", requestPayload);
            event.put("responsePayload", responsePayload);
            event.put("httpStatus", httpStatus);
            event.put("timestamp", LocalDateTime.now());

            rabbitTemplate.convertAndSend(deliveryExchange, "courier.api.call.log", event);
            log.debug("API Call logged via RabbitMQ - Endpoint: {}, Status: {}", endpoint, httpStatus);
        } catch (Exception e) {
            log.warn("Error logging API call via RabbitMQ: {}", e.getMessage());
            // Don't fail the main operation if logging fails
        }
    }

    /**
     * Build form URL-encoded request body
     */
    private String buildFormUrlEncodedBody(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(key).append("=").append(value);
        });
        return sb.toString();
    }

    /**
     * Publish order placed with courier event (async)
     */
    private void publishOrderPlacedWithCourierEvent(OrderEntity order, String wayBillId, String courierName) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderUuid", order.getUuid());
            event.put("orderId", order.getId());
            event.put("wayBillId", wayBillId);
            event.put("courierName", courierName);
            event.put("timestamp", LocalDateTime.now());

            rabbitTemplate.convertAndSend(orderExchange, "order.placed.with.courier", event);
            log.info("✅ Published order placed with courier event for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Error publishing order placed event: {}", e.getMessage());
        }
    }

    /**
     * Publish shipment placed with courier event (async)
     */
    private void publishShipmentPlacedWithCourierEvent(String shipmentUuid, String wayBillId, String courierName, Map<String, Object> courierResponse) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("shipmentUuid", shipmentUuid);
            event.put("wayBillId", wayBillId);
            event.put("courierName", courierName);
            event.put("courierResponse", courierResponse);
            event.put("timestamp", LocalDateTime.now());

            rabbitTemplate.convertAndSend(deliveryExchange, "shipment.placed.with.courier", event);

            log.info("✅ Published shipment placed with courier event for shipment: {}", shipmentUuid);
        } catch (Exception e) {
            log.error("Error publishing shipment placed event: {}", e.getMessage());
        }
    }

    /**
     * Publish payment transaction create event (async)
     */
    private void publishPaymentTransactionCreateEvent(OrderEntity order, BigDecimal codAmount) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderUuid", order.getUuid());
            event.put("orderId", order.getId());
            event.put("orderNumber", order.getOrderNumber());
            // Don't include methodId for COD/pending payments - let it be null
            // methodId will be populated when actual payment is processed
            event.put("amount", order.getTotal());
            event.put("codAmount", codAmount != null ? codAmount : order.getTotal());
            event.put("timestamp", LocalDateTime.now());

            rabbitTemplate.convertAndSend(paymentExchange, "order.courier.payment.transaction.create", event);

            log.info("✅ Published payment transaction create event for order: {} (Payment method will be set on actual payment)",
                    order.getOrderNumber());
        } catch (Exception e) {
            log.error("❌ Error publishing payment transaction create event: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional("orderTransactionManager")
    public AddPickupRequestResponse addPickupRequest(AddPickupRequestDto request) {
        log.info("=== ADD PICKUP REQUEST - START ===");
        log.info("Order UUID: {}, Vehicle Type: {}, Address: {}",
                request.getOrderUuid(), request.getVehicleType(), request.getPickupAddress());

        try {
            // 1. Fetch order to get orderId (Order module has the order)
            OrderEntity order = orderRepository.findByUuid(request.getOrderUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with UUID: " + request.getOrderUuid()));

            Long orderId = order.getId();
            log.info("✅ Order found: {} (ID: {})", order.getOrderNumber(), orderId);

            // 2. Get shipment details via RabbitMQ using orderId (which delivery module knows)
            Map<String, Object> shipmentData = getShipmentDetailsViaRabbitMQ(orderId);

            if (shipmentData == null || !(Boolean) shipmentData.get("success")) {
                throw new RuntimeException("Failed to fetch shipment details - " +
                        (shipmentData != null ? shipmentData.get("error") : "Unknown error"));
            }

            String shipmentUuid = (String) shipmentData.get("shipmentUuid");
            String wayBillId = (String) shipmentData.get("wayBillId");
            String courierApiKey = (String) shipmentData.get("apiKey");
            String courierApiBaseUrl = (String) shipmentData.get("apiBaseUrl");
            String courierName = (String) shipmentData.get("courierName");
            Long courierId = ((Number) shipmentData.get("courierId")).longValue();

            log.info("✅ Shipment found - UUID: {}, Waybill: {}, Courier: {}", shipmentUuid, wayBillId, courierName);

            // 3. Build Koombiyo pickup request
            Map<String, Object> koombiyoPickupRequest = buildKoombiyoPickupRequest(
                    courierApiKey,
                    request.getVehicleType(),
                    request.getPickupAddress(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getPhone(),
                    request.getQuantity(),
                    request.getPickupRemark()
            );

            // 4. Call Koombiyo Pickup API
            String pickupsEndpoint = "Pickups/users";
            log.info("Calling Koombiyo Pickups API: POST {}{}", courierApiBaseUrl, pickupsEndpoint);

            Map<String, Object> koombiyoApiResponse = callKoombiyoPickupsAPI(
                    courierApiBaseUrl,
                    courierApiKey,
                    koombiyoPickupRequest,
                    courierId
            );

            log.info("✅ Koombiyo Pickups API response received: {}", koombiyoApiResponse);

            // 5. Update shipment status via RabbitMQ event (to mark as pickup requested)
            publishPickupRequestedEvent(orderId, shipmentUuid, wayBillId, courierName,
                    request.getPickupAddress(), request.getVehicleType(), koombiyoApiResponse);

            // 6. Build and return response
            AddPickupRequestResponse response = AddPickupRequestResponse.builder()
                    .shipmentUuid(shipmentUuid)
                    .wayBillId(wayBillId)
                    .vehicleType(request.getVehicleType())
                    .pickupAddress(request.getPickupAddress())
                    .pickupStatus("REQUESTED")
                    .success(true)
                    .courierApiMessage((String) koombiyoApiResponse.getOrDefault("message", "Pickup request successfully submitted to Koombiyo"))
                    .httpStatus((Integer) koombiyoApiResponse.get("httpStatus"))
                    .apiResponseBody((String) koombiyoApiResponse.get("responseBody"))
                    .requestedAt(LocalDateTime.now())
                    .errorMessage(null)
                    .build();

            log.info("=== ADD PICKUP REQUEST - SUCCESS ===");
            return response;

        } catch (Exception e) {
            log.error("❌ Error in add pickup request: {}", e.getMessage(), e);

            return AddPickupRequestResponse.builder()
                    .vehicleType(request.getVehicleType())
                    .pickupAddress(request.getPickupAddress())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Get shipment details via RabbitMQ lookup using Order ID
     * Fetches shipment UUID, waybill ID and courier API credentials from delivery module
     * Order module passes orderId, and delivery module finds the shipment by orderId
     */
    private Map<String, Object> getShipmentDetailsViaRabbitMQ(Long orderId) {
        try {
            log.info("Fetching shipment details via RabbitMQ - Order ID: {}", orderId);

            Map<String, Object> request = new HashMap<>();
            request.put("requestType", "GET_SHIPMENT_FOR_PICKUP");
            request.put("orderId", orderId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(RABBITMQ_TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "shipment.lookup.request",
                    request
            );

            if (response instanceof Map) {
                return (Map<String, Object>) response;
            }

            log.error("Invalid response type from RabbitMQ: {}", response != null ? response.getClass() : "null");
            return null;
        } catch (Exception e) {
            log.error("Error fetching shipment details from RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build Koombiyo Pickup API request payload
     * Based on Koombiyo's pickup request parameters
     */
    private Map<String, Object> buildKoombiyoPickupRequest(
            String apiKey,
            String vehicleType,
            String pickupAddress,
            Double latitude,
            Double longitude,
            String phone,
            Integer quantity,
            String pickupRemark
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("apikey", apiKey);
        request.put("vehicleType", vehicleType);
        request.put("pickup_remark", pickupRemark != null ? pickupRemark : "");
        request.put("pickup_address", pickupAddress);
        request.put("latitude", latitude);
        request.put("longitude", longitude);
        request.put("phone", phone);
        request.put("qty", quantity);

        return request;
    }

    /**
     * Call Koombiyo Pickups API to add pickup request
     * Endpoint: POST /api/Pickups/users
     */
    private Map<String, Object> callKoombiyoPickupsAPI(
            String apiBaseUrl,
            String apiKey,
            Map<String, Object> requestPayload,
            Long courierId
    ) {
        try {
            String endpoint = apiBaseUrl + "Pickups/users";
            String requestBody = buildFormUrlEncodedBody(requestPayload);

            log.info("Calling Koombiyo Pickups API: {}", endpoint);
            log.debug("Request payload: {}", requestBody);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);

            org.springframework.http.ResponseEntity<String> apiResponse = restTemplate.postForEntity(
                    endpoint,
                    entity,
                    String.class
            );

            int httpStatus = apiResponse.getStatusCode().value();
            String responseBody = apiResponse.getBody();

            log.info("Koombiyo Pickups API Response [HTTP {}]: {}", httpStatus, responseBody);

            // Log API call via RabbitMQ
            logCourierApiCallViaRabbitMQ(courierId, endpoint, requestBody, responseBody, httpStatus);

            // Parse response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", httpStatus == 200);
            responseMap.put("httpStatus", httpStatus);
            responseMap.put("message", responseBody);
            responseMap.put("responseBody", responseBody);

            // Extract pick_id from Koombiyo response if available
            try {
                Map<String, Object> parsedResponse = objectMapper.readValue(responseBody, Map.class);
                if (parsedResponse.containsKey("pick_id")) {
                    responseMap.put("pickId", parsedResponse.get("pick_id"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse pick_id from response: {}", e.getMessage());
            }

            return responseMap;

        } catch (HttpStatusCodeException ex) {
            int httpStatus = ex.getStatusCode().value();
            String errorBody = ex.getResponseBodyAsString();

            log.error("Koombiyo Pickups API Error [HTTP {}]: {}", httpStatus, errorBody);

            // Log failed API call via RabbitMQ
            try {
                logCourierApiCallViaRabbitMQ(courierId, apiBaseUrl + "Pickups/users",
                        objectMapper.writeValueAsString(requestPayload), errorBody, httpStatus);
            } catch (Exception logEx) {
                log.error("Error logging failed pickup API call: {}", logEx.getMessage());
            }

            throw new RuntimeException("Koombiyo Pickups API call failed [HTTP " + httpStatus + "]: " + errorBody);
        } catch (Exception e) {
            log.error("Unexpected error calling Koombiyo Pickups API: {}", e.getMessage(), e);

            // Log error via RabbitMQ
            String requestPayloadStr;
            try {
                requestPayloadStr = objectMapper.writeValueAsString(requestPayload);
            } catch (Exception ex) {
                requestPayloadStr = requestPayload.toString();
            }

            try {
                logCourierApiCallViaRabbitMQ(courierId, apiBaseUrl + "Pickups/users",
                        requestPayloadStr, e.getMessage(), 0);
            } catch (Exception logEx) {
                log.error("Error logging pickup API call exception: {}", logEx.getMessage());
            }

            throw new RuntimeException("Error calling Koombiyo Pickups API: " + e.getMessage());
        }
    }

    /**
     * Publish pickup requested event (async)
     * Notifies delivery module that a pickup has been requested
     */
    private void publishPickupRequestedEvent(Long orderId, String shipmentUuid, String wayBillId, String courierName,
                                            String pickupAddress, String vehicleType, Map<String, Object> courierResponse) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", orderId);
            event.put("shipmentUuid", shipmentUuid);
            event.put("wayBillId", wayBillId);
            event.put("courierName", courierName);
            event.put("pickupAddress", pickupAddress);
            event.put("vehicleType", vehicleType);
            event.put("pickupStatus", "REQUESTED");
            event.put("courierResponse", courierResponse);

            // Extract pickupId from courierResponse
            Long pickupId = null;
            if (courierResponse != null) {
                Object pickupIdObj = courierResponse.get("pickId");
                if (pickupIdObj instanceof Number) {
                    pickupId = ((Number) pickupIdObj).longValue();
                    log.info("Extracted pickupId from courier response: {}", pickupId);
                }
            }
            event.put("pickupId", pickupId);
            event.put("timestamp", LocalDateTime.now());

            rabbitTemplate.convertAndSend(deliveryExchange, "shipment.pickup.requested", event);
            log.info("✅ Published pickup requested event for shipment: {} with pickupId: {}", shipmentUuid, pickupId);
        } catch (Exception e) {
            log.error("Error publishing pickup requested event: {}", e.getMessage());
        }
    }
}

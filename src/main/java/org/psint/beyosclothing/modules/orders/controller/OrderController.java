package org.psint.beyosclothing.modules.orders.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.orders.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.OrderPlacementResponse;
import org.psint.beyosclothing.modules.orders.service.OrderPlacementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller
 * Handles order placement and management endpoints
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderPlacementService orderPlacementService;

    @PostMapping
    @Operation(summary = "Place an order", description = "Create a new order from cart items")
    public ResponseEntity<OrderPlacementResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {

        log.info("Received place order request - Customer UUID: {}, Payment Method: {}, Courier: {}",
                customerUuid, request.getPaymentMethodId(), request.getCourierId());

        // Validate that either customerUuid or guestToken is provided
        if (customerUuid == null && guestToken == null) {
            throw new IllegalArgumentException("Either Customer UUID or Guest Token must be provided");
        }

        OrderPlacementResponse response = orderPlacementService.placeOrder(
                request, customerUuid, guestToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get order details", description = "Retrieve detailed information about a specific order")
    public ResponseEntity<OrderDetailResponse> getOrderDetails(
            @PathVariable String uuid,
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {

        log.debug("Fetching order details - UUID: {}, Customer UUID: {}", uuid, customerUuid);

        OrderDetailResponse response = orderPlacementService.getOrderDetails(uuid, customerUuid);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get customer orders", description = "Retrieve paginated list of customer's orders")
    public ResponseEntity<Page<OrderDetailResponse>> getCustomerOrders(
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            Pageable pageable) {

        log.debug("Fetching customer orders - Customer UUID: {}, Page: {}, Size: {}",
                customerUuid, pageable.getPageNumber(), pageable.getPageSize());

        // Validate that either customerUuid or guestToken is provided
        if (customerUuid == null && guestToken == null) {
            throw new IllegalArgumentException("Either Customer UUID or Guest Token must be provided");
        }

        Page<OrderDetailResponse> response = orderPlacementService.getCustomerOrders(customerUuid, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uuid}/tracking")
    @Operation(summary = "Get order tracking information", description = "Retrieve shipment tracking details for an order")
    public ResponseEntity<OrderDetailResponse.TrackingInfo> getOrderTracking(
            @PathVariable String uuid,
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {

        log.debug("Fetching order tracking - UUID: {}, Customer UUID: {}", uuid, customerUuid);

        OrderDetailResponse orderDetails = orderPlacementService.getOrderDetails(uuid, customerUuid);

        return ResponseEntity.ok(orderDetails.getTracking());
    }
}

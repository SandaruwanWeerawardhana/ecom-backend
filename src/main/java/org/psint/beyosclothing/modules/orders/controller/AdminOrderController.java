package org.psint.beyosclothing.modules.orders.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderWithCourierRequest;
import org.psint.beyosclothing.modules.orders.dto.request.AddPickupRequestDto;
import org.psint.beyosclothing.modules.orders.dto.request.RejectOrderRequest;
import org.psint.beyosclothing.modules.orders.dto.request.UpdateOrderStatusRequest;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderListResponse;
import org.psint.beyosclothing.modules.orders.dto.response.PlaceOrderWithCourierResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AddPickupRequestResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.service.AdminOrderCourierService;
import org.psint.beyosclothing.modules.orders.service.AdminOrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;

/**
 * Admin Order Controller
 * Provides admin-only order management APIs
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Orders", description = "Admin order management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final AdminOrderCourierService adminOrderCourierService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get all orders (Admin)",
            description = "Retrieve paginated list of all orders with optional filters. " +
                    "Filters: customer name search, status, order type (ONLINE/POS), order from (CUSTOMER/RESELLER), date range. " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
    })
    public ResponseEntity<APIResponse<PageResponse<AdminOrderListResponse>>> getAllOrders(

            @Parameter(description = "Search by order number, customer name, phone, or email (partial match, case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter by order status: PENDING, PAID, PROCESSING, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELLED, RETURN_REQUESTED, RETURN_APPROVED, REFUND_INITIATED, REFUNDED")
            @RequestParam(required = false) OrderEntity.OrderStatus status,

            @Parameter(description = "Filter by order type: ONLINE or POS")
            @RequestParam(required = false) OrderEntity.OrderSource source,

            @Parameter(description = "Filter by order origin: CUSTOMER or RESELLER")
            @RequestParam(required = false) String orderFrom,

            @Parameter(description = "Filter orders from this date (inclusive). Format: yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Filter orders up to this date (inclusive). Format: yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Zero-based page index (default: 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (default: 20)")
            @RequestParam(defaultValue = "20") int size
    ) {

        log.info("Admin GET /api/v1/admin/orders - search: {}, status: {}, source: {}, orderFrom: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                search, status, source, orderFrom, startDate, endDate, page, size);

        // Validate orderFrom value if provided
        if (orderFrom != null && !orderFrom.isBlank()) {
            String upper = orderFrom.trim().toUpperCase();
            if (!upper.equals("CUSTOMER") && !upper.equals("RESELLER")) {
                throw new org.psint.beyosclothing.core.exception.BadRequestException(
                        "Invalid orderFrom value. Accepted values are: CUSTOMER, RESELLER");
            }
        }

        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException(
                    "startDate must not be after endDate");
        }

        // Validate page and size
        if (page < 0) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page size must be between 1 and 100");
        }

        PageResponse<AdminOrderListResponse> response = adminOrderService.getAllOrders(
                search, status, source, orderFrom, startDate, endDate, page, size);

        log.info("Admin orders retrieved - total: {}, page: {}/{}", response.getTotalElements(), page, response.getTotalPages());

        return ResponseEntity.ok(APIResponse.success("Orders retrieved successfully", response));
    }

    @GetMapping("/pending")
//    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get all pending orders (Admin)",
            description = "Retrieve paginated list of orders with status PENDING only. " +
                    "Optional filters: order type (ONLINE/POS) and order from (CUSTOMER/RESELLER). " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
    })
    public ResponseEntity<APIResponse<PageResponse<AdminOrderListResponse>>> getPendingOrders(
            @Parameter(description = "Filter by order type: ONLINE or POS")
            @RequestParam(required = false) String orderType,

            @Parameter(description = "Filter by order origin: CUSTOMER or RESELLER")
            @RequestParam(required = false) String orderFrom,

            @Parameter(description = "Zero-based page index (default: 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (default: 20)")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Admin GET /api/v1/admin/orders/pending - orderType: {}, orderFrom: {}, page: {}, size: {}",
                orderType, orderFrom, page, size);

        OrderEntity.OrderSource orderTypeFilter = null;
        if (orderType != null && !orderType.isBlank()) {
            try {
                orderTypeFilter = OrderEntity.OrderSource.valueOf(orderType.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new org.psint.beyosclothing.core.exception.BadRequestException(
                        "Invalid orderType value. Accepted values are: ONLINE, POS");
            }
        }

        String normalizedOrderFrom = null;
        if (orderFrom != null && !orderFrom.isBlank()) {
            normalizedOrderFrom = orderFrom.trim().toUpperCase();
            if (!normalizedOrderFrom.equals("CUSTOMER") && !normalizedOrderFrom.equals("RESELLER")) {
                throw new org.psint.beyosclothing.core.exception.BadRequestException(
                        "Invalid orderFrom value. Accepted values are: CUSTOMER, RESELLER");
            }
        }

        if (page < 0) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page size must be between 1 and 100");
        }

        PageResponse<AdminOrderListResponse> response = adminOrderService.getPendingOrders(
                orderTypeFilter,
                normalizedOrderFrom,
                page,
                size);

        log.info("Admin pending orders retrieved - total: {}, page: {}/{}",
                response.getTotalElements(), page, response.getTotalPages());

        return ResponseEntity.ok(APIResponse.success("Pending orders retrieved successfully", response));
    }

    @GetMapping("/pending/{uuid}")
//    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get pending order detail by UUID (Admin)",
            description = "Retrieve full order detail by UUID only when order status is PENDING. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending order detail retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format or order is not pending"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<APIResponse<AdminOrderDetailResponse>> getPendingOrderDetail(
            @Parameter(description = "Order UUID", example = "d2f27fd1-9d76-4c12-802c-13e70bf2aa91")
            @PathVariable String uuid) {
        return fetchAndBuildOrderDetailResponse(uuid, true, "Pending order detail retrieved successfully");
    }



    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get order detail (Admin)",
            description = "Retrieve full order detail by UUID. Works for both CUSTOMER and RESELLER orders. " +
                    "For reseller orders, items are fetched via RabbitMQ from the reseller cart. " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order detail retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<APIResponse<AdminOrderDetailResponse>> getOrderDetail(
            @Parameter(description = "Order UUID", example = "d2f27fd1-9d76-4c12-802c-13e70bf2aa91")
            @PathVariable String uuid) {

        log.info("Admin GET /api/v1/admin/orders/{}", uuid);

        return fetchAndBuildOrderDetailResponse(uuid, false, "Order detail retrieved successfully");
    }

    private ResponseEntity<APIResponse<AdminOrderDetailResponse>> fetchAndBuildOrderDetailResponse(
            String uuid,
            boolean pendingOnly,
            String successMessage) {
        if (uuid == null || uuid.isBlank()) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Order UUID must not be blank");
        }

        AdminOrderDetailResponse response = pendingOnly
                ? adminOrderService.getPendingOrderDetail(uuid)
                : adminOrderService.getOrderDetail(uuid);

        log.info("Admin {}order detail retrieved - orderNumber: {}, orderFrom: {}",
                pendingOnly ? "pending " : "", response.getOrderNumber(), response.getOrderFrom());

        return ResponseEntity.ok(APIResponse.success(successMessage, response));
    }

    @PostMapping("/place-with-courier")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Place order with courier",
            description = "Place an already-created order with a courier service (Koombiyo). " +
                    "This API initializes courier pickup and handles order tracking. " +
                    "Updates order status to PROCESSING and shipment status to BOOKED. " +
                    "Creates payment transaction record for COD collection. " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order successfully placed with courier",
                    content = @Content(schema = @Schema(implementation = PlaceOrderWithCourierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order or courier not found"),
            @ApiResponse(responseCode = "500", description = "Courier API error or internal server error")
    })
    public ResponseEntity<APIResponse<PlaceOrderWithCourierResponse>> placeOrderWithCourier(
            @Valid @RequestBody PlaceOrderWithCourierRequest request
    ) {
        log.info("Admin POST /api/v1/admin/orders/place-with-courier - Order UUID: {}, Waybill ID: {}",
                request.getOrderUuid(), request.getWayBillId());

        try {
            PlaceOrderWithCourierResponse response = adminOrderCourierService.placeOrderWithCourier(request);

            if (response.getSuccess()) {
                log.info("✅ Order placed with courier - Order: {}, Waybill: {}, Expected Delivery: {}",
                        response.getOrderNumber(), response.getWayBillId(), response.getExpectedDeliveryDate());

                return ResponseEntity.ok(APIResponse.success(
                        "Order successfully placed with " + response.getCourierName() + " courier",
                        response
                ));
            } else {
                log.error("❌ Failed to place order with courier: {}", response.getErrorMessage());

                return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(APIResponse.error(response.getErrorMessage()));
            }
        } catch (Exception e) {
            log.error("❌ Error placing order with courier: {}", e.getMessage(), e);

            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.error("Error placing order with courier: " + e.getMessage()));
        }
    }

    @PostMapping("/add-pickup-request")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Add pickup request to courier",
            description = "Add a pickup request to Koombiyo courier service for an order's shipment. " +
                    "This API initiates pickup from a specific location with vehicle type and coordinates. " +
                    "Updates shipment status to reflect pickup request. " +
                    "API response from Koombiyo is logged for tracking. " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pickup request successfully added",
                    content = @Content(schema = @Schema(implementation = AddPickupRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order or shipment not found"),
            @ApiResponse(responseCode = "500", description = "Courier API error or internal server error")
    })
    public ResponseEntity<APIResponse<AddPickupRequestResponse>> addPickupRequest(
            @Valid @RequestBody AddPickupRequestDto request
    ) {
        log.info("Admin POST /api/v1/admin/orders/add-pickup-request - Order UUID: {}, Vehicle Type: {}, Address: {}",
                request.getOrderUuid(), request.getVehicleType(), request.getPickupAddress());

        try {
            AddPickupRequestResponse response = adminOrderCourierService.addPickupRequest(request);

            if (response.getSuccess()) {
                log.info("✅ Pickup request added - Shipment: {}, Waybill: {}, Vehicle: {}, Status: {}",
                        response.getShipmentUuid(), response.getWayBillId(), response.getVehicleType(), response.getPickupStatus());
                log.debug("Koombiyo Pickup API Response [HTTP {}]: {}", response.getHttpStatus(), response.getApiResponseBody());

                return ResponseEntity.ok(APIResponse.success(
                        "Pickup request successfully submitted to courier",
                        response
                ));
            } else {
                log.error("❌ Failed to add pickup request: {}", response.getErrorMessage());

                return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(APIResponse.error(response.getErrorMessage()));
            }
        } catch (Exception e) {
            log.error("❌ Error adding pickup request: {}", e.getMessage(), e);

            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.error("Error adding pickup request: " + e.getMessage()));
        }
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Reject an order (Admin)",
            description = "Reject an existing order. Sets order status to REJECT and records a rejection reason and optional admin notes. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order rejected successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or order cannot be rejected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<APIResponse<AdminOrderDetailResponse>> rejectOrder(
            @Parameter(description = "Order UUID")
            @PathVariable String uuid,
            @Valid @RequestBody RejectOrderRequest request
    ) {
        log.info("Admin POST /api/v1/admin/orders/{}/reject - reason: {}", uuid, request.getReason());

        AdminOrderDetailResponse response = adminOrderService.rejectOrder(uuid, request.getReason(), request.getAdminNotes());
        return ResponseEntity.ok(
                APIResponse.success(
                        "Order rejected successfully", response)
        );
    }

    @PatchMapping("/{uuid}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Update order status (Admin)",
            description = "Update an order's status. Records the change in order history and sends an SMS " +
                    "to the order's owner (customer or reseller) with the new status. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order status updated successfully",
                    content = @Content(schema = @Schema(implementation = AdminOrderDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or order already in that status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<APIResponse<AdminOrderDetailResponse>> updateOrderStatus(
            @Parameter(description = "Order UUID")
            @PathVariable String uuid,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        log.info("Admin PATCH /api/v1/admin/orders/{}/status - status: {}", uuid, request.getStatus());

        AdminOrderDetailResponse response = adminOrderService.updateOrderStatus(uuid, request.getStatus(), request.getNotes());
        return ResponseEntity.ok(
                APIResponse.success("Order status updated successfully", response)
        );
    }
}

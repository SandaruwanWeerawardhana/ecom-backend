package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerOrderPlacementRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerOrderService;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST Controller for Reseller Order Management
 */
@RestController
@RequestMapping("/api/v1/resellers/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Orders", description = "Order placement and management for resellers")
public class ResellerOrderController {

    private final ResellerOrderService orderService;
    private final ResellerSecurityService securityService;
    private final JwtUtil jwtUtil;

    /**
     * Place order for reseller's customer
     */
    @PostMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Place order",
               description = "Places order with customer details. Cart items automatically used. COD payment only. Requires APPROVED status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Order placed successfully",
                    content = @Content(schema = @Schema(implementation = ResellerOrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error - empty cart, invalid customer details, or insufficient stock"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<ResellerOrderResponse> placeOrder(
            Authentication authentication,
            @Valid @RequestBody ResellerOrderPlacementRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Placing order for reseller user ID: {} - Customer: {}", userId, request.getCustomerName());
        ResellerOrderResponse response = orderService.placeOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get orders list with pagination and filters
     */
    @GetMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get all orders",
            description = "Retrieve paginated list of reseller's orders with optional filters. " +
                    "Filters: customer name/order number search, status, date range. " +
                    "Includes order summary with customer details and amounts."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - RESELLER role required")
    })
    public ResponseEntity<APIResponse<PageResponse<ResellerOrderListResponse.OrderSummary>>> getOrders(
            Authentication authentication,

            @RequestParam(required = false) String search,

            @RequestParam(required = false) String status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        Long userId = jwtUtil.getUserId(authentication);

        log.info("Reseller GET /api/v1/resellers/orders - userId: {}, search: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                userId, search, status, startDate, endDate, page, size);

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

        PageResponse<ResellerOrderListResponse.OrderSummary> response = orderService.getOrdersWithFilters(
                userId, search, status, startDate, endDate, page, size);

        log.info("Reseller orders retrieved - total: {}, page: {}/{}", response.getTotalElements(), page, response.getTotalPages());

        return ResponseEntity.ok(APIResponse.success("Orders retrieved successfully", response));
    }

    /**
     * Get reseller orders by reseller UUID
     */
    @GetMapping("/by-reseller/{resellerUuid}")
    @PreAuthorize("hasAnyRole('RESELLER', 'ADMIN')")
    @Operation(
            summary = "Get reseller orders by reseller UUID",
            description = "Retrieve paginated list of orders for a reseller identified by UUID. " +
                    "Returns order number, order date, customer name, amount, and status. " +
                    "Uses RabbitMQ to fetch the order list from the order module."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - RESELLER role required"),
            @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<APIResponse<PageResponse<ResellerOrderListResponse.OrderSummary>>> getOrdersByResellerUuid(
            @PathVariable String resellerUuid,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Fetching reseller orders by UUID - resellerUuid: {}, search: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                resellerUuid, search, status, startDate, endDate, page, size);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("startDate must not be after endDate");
        }
        if (page < 0) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Page size must be between 1 and 100");
        }

        PageResponse<ResellerOrderListResponse.OrderSummary> response = orderService.getOrdersByResellerUuid(
                resellerUuid, search, status, startDate, endDate, page, size);

        return ResponseEntity.ok(APIResponse.success("Orders retrieved successfully", response));
    }

    /**
     * Get pending orders list with pagination and filters
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get pending orders",
            description = "Retrieve paginated list of reseller's pending orders (PENDING, PROCESSING, OUT_FOR_DELIVERY) with optional filters. " +
                    "Filters: customer name/order number search, date range. " +
                    "Includes order summary with customer details and amounts."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - RESELLER role required")
    })
    public ResponseEntity<APIResponse<PageResponse<ResellerOrderListResponse.OrderSummary>>> getPendingOrders(
            Authentication authentication,

            @RequestParam(required = false) String search,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        Long userId = jwtUtil.getUserId(authentication);

        log.info("Reseller GET /api/v1/resellers/orders/pending - userId: {}, search: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                userId, search, startDate, endDate, page, size);

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

        PageResponse<ResellerOrderListResponse.OrderSummary> response = orderService.getPendingOrdersWithFilters(
                userId, search, startDate, endDate, page, size);

        log.info("Reseller pending orders retrieved - total: {}, page: {}/{}", response.getTotalElements(), page, response.getTotalPages());

        return ResponseEntity.ok(APIResponse.success("Pending orders retrieved successfully", response));
    }

    /**
     * Get order details by UUID
     */
    @GetMapping("/{orderUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get order details",
               description = "Returns complete order information including customer details, order items, payment info, tracking, and order history. " +
                       "Displays full order breakdown matching the Order Details UI with all financial calculations and status information.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerOrderDetailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid order UUID format"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not reseller's order or lacks RESELLER role"),
        @ApiResponse(responseCode = "404", description = "Order not found with the provided UUID"),
        @ApiResponse(responseCode = "500", description = "Service unavailable - order service timeout or database error")
    })
    public ResponseEntity<APIResponse<ResellerOrderDetailResponse>> getOrderDetails(
            Authentication authentication,
            @PathVariable String orderUuid) {
        Long userId = jwtUtil.getUserId(authentication);

        log.info("Fetching order details for order UUID: {} by reseller user ID: {}", orderUuid, userId);

        try {
            ResellerOrderDetailResponse response = orderService.getOrderByUuid(userId, orderUuid);
            log.info("Order details retrieved successfully for order UUID: {}", orderUuid);
            return ResponseEntity.ok(APIResponse.success("Order details retrieved successfully", response));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order UUID format: {}", orderUuid);
            throw new org.psint.beyosclothing.core.exception.BadRequestException("Invalid order UUID format. Must be a valid UUID v4");
        } catch (SecurityException e) {
            log.warn("Access denied - reseller {} attempting to access order {}", userId, orderUuid);
            throw new org.psint.beyosclothing.core.exception.ForbiddenException("You do not have permission to view this order");
        } catch (org.psint.beyosclothing.core.exception.ResourceNotFoundException e) {
            log.warn("Order not found: {}", orderUuid);
            throw e;
        } catch (Exception e) {
            log.error("Error fetching order details for UUID: {} by user: {}", orderUuid, userId, e);
            throw new org.psint.beyosclothing.core.exception.ServiceException("Failed to fetch order details from order service. Please try again later.");
        }
    }

    /**
     * Get profit breakdown for an order
     */
    @GetMapping("/{orderUuid}/profit")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get profit breakdown",
               description = "Returns detailed profit analysis for an order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profit breakdown retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<ResellerOrderDetailResponse> getProfitBreakdown(
            Authentication authentication,
            @PathVariable String orderUuid) {
        Long userId = jwtUtil.getUserId(authentication);

        log.info("Fetching profit breakdown for order UUID: {} by reseller user ID: {}", orderUuid, userId);

        // Return the same order details which includes profit information
        ResellerOrderDetailResponse response = orderService.getOrderByUuid(userId, orderUuid);
        return ResponseEntity.ok(response);
    }
}

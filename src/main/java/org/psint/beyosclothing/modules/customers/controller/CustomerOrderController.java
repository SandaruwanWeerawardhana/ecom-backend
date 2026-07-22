package org.psint.beyosclothing.modules.customers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.customers.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.request.CreateWishlistItemRequest;
import org.psint.beyosclothing.modules.customers.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateOrderPaymentStatusRequest;
import org.psint.beyosclothing.modules.customers.dto.response.CartResponseDTO;
import org.psint.beyosclothing.modules.customers.dto.response.CustomerDashboardCountsResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderPaymentStatusResponse;
import org.psint.beyosclothing.modules.customers.dto.response.WishlistItemResponse;
import org.psint.beyosclothing.modules.customers.service.CustomerOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer Order Controller
 * Handles customer-scoped order endpoints
 */
@RestController
@RequestMapping("/api/v1/customers/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Orders", description = "Customer order management APIs")
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Create an order", description = "Place a new order for the authenticated customer")
    public ResponseEntity<APIResponse<OrderDetailResponse>> createOrder(
            @RequestParam String customerUuid,
            @Valid @RequestBody PlaceOrderRequest request) {

        log.info("POST /api/v1/customers/orders - Customer UUID: {}", customerUuid);

        OrderDetailResponse response = customerOrderService.createOrder(request, customerUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<OrderDetailResponse>builder()
                        .success(true)
                        .message("Order placed successfully")
                        .data(response)
                        .build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    @Operation(summary = "Get customer orders", description = "Retrieve all order details for a customer")
    public ResponseEntity<APIResponse<List<OrderDetailResponse>>> getOrderById(
            @RequestParam String customerUuid) {

        log.info("GET /api/v1/customers/orders - Customer UUID: {}", customerUuid);

        List<OrderDetailResponse> response = customerOrderService.getOrderById(customerUuid);

        return ResponseEntity.ok(APIResponse.<List<OrderDetailResponse>>builder()
                .success(true)
                .message("Customer orders retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/{orderUuid}/payment-status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Get order payment status",
            description = "Returns the current order and payment status for a single order. The frontend polls "
                    + "this after an online checkout to detect when the asynchronous gateway callback has "
                    + "settled the payment.")
    public ResponseEntity<APIResponse<OrderPaymentStatusResponse>> getOrderPaymentStatus(
            @PathVariable String orderUuid,
            @RequestParam String customerUuid) {

        log.info("GET /api/v1/customers/orders/{}/payment-status - Customer UUID: {}", orderUuid, customerUuid);

        OrderPaymentStatusResponse response = customerOrderService.getOrderPaymentStatus(orderUuid, customerUuid);

        return ResponseEntity.ok(APIResponse.<OrderPaymentStatusResponse>builder()
                .success(true)
                .message("Order payment status retrieved successfully")
                .data(response)
                .build());
    }

    @PostMapping("/update/{orderUuid}/payment-status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Update order payment status",
            description = "Accepts the client-reported payment status (e.g. UNPAID after an abandoned checkout) "
                    + "and re-verifies the order's payment against the gateway's transaction status API. PAID "
                    + "cannot be set by the client; the gateway verification result always takes precedence, and "
                    + "a payment newly confirmed as PAID also triggers order finalization.")
    public ResponseEntity<APIResponse<OrderPaymentStatusResponse>> updateOrderPaymentStatus(
            @PathVariable String orderUuid,
            @RequestParam String customerUuid,
            @Valid @RequestBody UpdateOrderPaymentStatusRequest request) {

        log.info("POST /api/v1/customers/orders/update/{}/payment-status - Customer UUID: {}, reported status: {}",
                orderUuid, customerUuid, request.getPaymentStatus());

        OrderPaymentStatusResponse response = customerOrderService.updateOrderPaymentStatus(orderUuid, customerUuid, request);

        return ResponseEntity.ok(APIResponse.<OrderPaymentStatusResponse>builder()
                .success(true)
                .message("Order payment status verified successfully")
                .data(response)
                .build());
    }

    @PostMapping("/cart")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Create cart", description = "Create or get existing cart for the customer, optionally adding an item")
    public ResponseEntity<APIResponse<CartResponseDTO>> createCart(
            @RequestParam String customerUuid,
            @RequestBody(required = false) AddToCartRequestDTO request) {

        log.info("POST /api/v1/customers/orders/cart - Customer UUID: {}", customerUuid);

        CartResponseDTO response = customerOrderService.createCart(customerUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<CartResponseDTO>builder()
                        .success(true)
                        .message("Cart created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/cart/{uuid}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    @Operation(summary = "Get cart by UUID", description = "Retrieve cart details by its UUID")
    public ResponseEntity<APIResponse<CartResponseDTO>> getCartById(
            @PathVariable String uuid,
            @RequestParam String customerUuid) {

        log.info("GET /api/v1/customers/orders/cart/{} - Customer UUID: {}", uuid, customerUuid);

        CartResponseDTO response = customerOrderService.getCartById(uuid, customerUuid);

        return ResponseEntity.ok(APIResponse.<CartResponseDTO>builder()
                .success(true)
                .message("Cart retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/dashboard-counts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    @Operation(summary = "Get customer dashboard counts", description = "Retrieve total orders, pending orders, and wishlist item counts")
    public ResponseEntity<APIResponse<CustomerDashboardCountsResponse>> getCustomerDashboardCounts(
            @RequestParam String customerUuid) {

        log.info("GET /api/v1/customers/orders/dashboard-counts - Customer UUID: {}", customerUuid);
        CustomerDashboardCountsResponse response = customerOrderService.getCustomerDashboardCounts(customerUuid);

        return ResponseEntity.ok(APIResponse.<CustomerDashboardCountsResponse>builder()
                .success(true)
                .message("Customer dashboard counts retrieved successfully")
                .data(response)
                .build());
    }

    @PostMapping("/wishlist")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Create wishlist item", description = "Add a product to the customer's wishlist")
    public ResponseEntity<APIResponse<WishlistItemResponse>> createWishlistItem(
            @RequestParam String customerUuid,
            @Valid @RequestBody CreateWishlistItemRequest request) {

        log.info("POST /api/v1/customers/orders/wishlist - Customer UUID: {}", customerUuid);

        WishlistItemResponse response = customerOrderService.createWishlistItem(customerUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<WishlistItemResponse>builder()
                        .success(true)
                        .message("Wishlist item created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/wishlist")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    @Operation(summary = "Get wishlist by customer UUID", description = "Retrieve all wishlist items for a customer")
    public ResponseEntity<APIResponse<List<WishlistItemResponse>>> getWishlistByCustomerUuid(
            @RequestParam String customerUuid) {

        log.info("GET /api/v1/customers/orders/wishlist - Customer UUID: {}", customerUuid);

        List<WishlistItemResponse> response = customerOrderService.getWishlistItemsByCustomerUuid(customerUuid);

        return ResponseEntity.ok(APIResponse.<List<WishlistItemResponse>>builder()
                .success(true)
                .message("Wishlist items retrieved successfully")
                .data(response)
                .build());
    }

    @DeleteMapping("/wishlist/{uuid}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    @Operation(summary = "Delete wishlist item", description = "Soft delete a customer's wishlist item by setting is_available to false")
    public ResponseEntity<APIResponse<Void>> deleteWishlistItem(
            @PathVariable String uuid,
            @RequestParam String customerUuid) {

        log.info("DELETE /api/v1/customers/orders/wishlist/{} - Customer UUID: {}", uuid, customerUuid);

        customerOrderService.deleteWishlistItem(uuid, customerUuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Wishlist item deleted successfully")
                .build());
    }
}

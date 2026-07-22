package org.psint.beyosclothing.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.payment.dto.request.CheckoutRequest;
import org.psint.beyosclothing.modules.payment.dto.response.CheckoutResponse;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;
import org.psint.beyosclothing.modules.payment.service.CheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Checkout Controller
 * Handles checkout preparation and summary calculation
 */
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Checkout", description = "APIs for checkout page and order preparation")
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/prepare")
    @Operation(
            summary = "Prepare checkout summary",
            description = "Get complete checkout summary including cart items, delivery address, shipping cost, and price breakdown. " +
                    "Supports both authenticated customers and guest users. " +
                    "Aggregates data from Cart, Customer, Delivery, and Payment modules."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Checkout summary prepared successfully",
                    content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Cart not found, customer not found, or courier/payment method not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<APIResponse<CheckoutResponse>> prepareCheckout(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,

            @Parameter(description = "Guest session token from cart cookie")
            @CookieValue(value = "guest_session_token", required = false) String guestSessionToken,

            @Valid @RequestBody CheckoutRequest request) {

        log.info("REST request to prepare checkout - Customer UUID: {}, Has Guest Token: {}, Courier: {}, Payment Method: {}",
                customerUuid, guestSessionToken != null, request.getCourierUuid(), request.getPaymentMethodUuid());

        if (customerUuid == null && guestSessionToken == null) {
            return ResponseEntity.badRequest()
                    .body(APIResponse.error("Either customer UUID or guest session token must be provided"));
        }

        CheckoutResponse response = checkoutService.prepareCheckout(customerUuid, guestSessionToken, request);

        log.info("Checkout prepared successfully - Total: {}, Items: {}",
                response.getPriceBreakdown().getTotal(), response.getItems().size());

        return ResponseEntity.ok(APIResponse.success("Checkout summary prepared successfully", response));
    }

    /**
     * GET /api/v1/checkout/payment-methods?cartUuid={cartUuid}&type={type}
     *
     * Returns the payment methods that are allowed for ALL products in the given cart.
     * The intersection logic ensures only mutually supported methods are shown.
     *
     * Example:
     *   - Product A supports: COD, BANK_CARD
     *   - Product B supports: COD only
     *   → Returns: COD only
     *
     * Response shape is identical to getAllPaymentMethods in PaymentMethodController.
     */
    @GetMapping("/payment-methods")
    @Operation(
            summary = "Get available payment methods for a cart",
            description = "Resolves which payment methods are available for checkout based on the products in the cart. " +
                    "Returns only the payment methods that are supported by ALL products in the cart (intersection). " +
                    "For example: if product A supports COD+BANK_CARD and product B supports COD only, only COD is returned. " +
                    "This endpoint must be called before showing payment options on the checkout page."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "400", description = "cartUuid is required"),
            @ApiResponse(responseCode = "404", description = "Cart not found or cart is empty")
    })
    public ResponseEntity<APIResponse<List<PaymentMethodResponse>>> getCartPaymentMethods(
            @Parameter(description = "UUID of the cart", required = true, example = "abc123-def456-...")
            @RequestParam String cartUuid,

            @Parameter(description = "Optional filter by payment type (ONLINE, OFFLINE, POS)")
            @RequestParam(required = false) String type) {

        log.info("REST request to get payment methods for cart UUID: {}, type filter: {}", cartUuid, type);

        if (cartUuid == null || cartUuid.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(APIResponse.error("cartUuid is required"));
        }

        List<PaymentMethodResponse> responses = checkoutService.getPaymentMethodsForCart(cartUuid, type);

        log.info("Returning {} payment method(s) for cart UUID: {}", responses.size(), cartUuid);

        return ResponseEntity.ok(APIResponse.success(
                "Payment methods for cart retrieved successfully", responses));
    }
}

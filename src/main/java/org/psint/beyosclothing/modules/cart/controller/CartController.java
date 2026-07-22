package org.psint.beyosclothing.modules.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ErrorResponse;
import org.psint.beyosclothing.modules.cart.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.ApplyPromoCodeRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.UpdateCartItemRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.response.CartResponseDTO;
import org.psint.beyosclothing.modules.cart.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cart Controller
 * Handles all cart operations for both guest and authenticated customers
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart", description = "Shopping cart management APIs")
public class CartController {

    private final CartService cartService;

    private static final String GUEST_SESSION_COOKIE = "guest_session_token";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days

    @GetMapping
    @Operation(
            summary = "Get cart",
            description = "Get current user's cart (guest or customer). Returns empty cart if none exists. Automatically generates guest session token if needed."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> getCart(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            HttpServletResponse response) {

        log.info("Get cart - customerUuid: {}, guestSessionToken received: {}",
                customerUuid,
                guestSessionToken != null ? guestSessionToken : "NULL - will generate new token");

        // Generate guest session token if neither exists
        if (customerUuid == null && guestSessionToken == null) {
            guestSessionToken = cartService.generateGuestSessionToken();
            addGuestSessionCookie(response, guestSessionToken);
            log.info("Generated new guest session token: {}", guestSessionToken);
        }

        CartResponseDTO cart = cartService.getCart(customerUuid, guestSessionToken);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    @Operation(
            summary = "Add item to cart",
            description = "Add a product or variant to cart. Creates cart if it doesn't exist. Merges quantity if item already exists."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Item added to cart successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or product out of stock",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer or product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> addToCart(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            @Valid @RequestBody AddToCartRequestDTO request,
            HttpServletResponse response) {

        log.info("Add to cart - customerUuid: {}, productUuid: {}", customerUuid, request.getProductUuid());

        // Generate guest session token if neither exists
        if (customerUuid == null && guestSessionToken == null) {
            guestSessionToken = cartService.generateGuestSessionToken();
            addGuestSessionCookie(response, guestSessionToken);
        }

        CartResponseDTO cart = cartService.addToCart(customerUuid, guestSessionToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PatchMapping("/items/{itemUuid}")
    @Operation(
            summary = "Update cart item",
            description = "Update quantity of a cart item. Set quantity to 0 to remove the item."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart item updated successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or item doesn't belong to cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart item or customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> updateCartItem(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            @Parameter(description = "UUID of the cart item to update", required = true)
            @PathVariable String itemUuid,
            @Valid @RequestBody UpdateCartItemRequestDTO request) {

        log.info("Update cart item {} - quantity: {}", itemUuid, request.getQuantity());

        CartResponseDTO cart = cartService.updateCartItem(customerUuid, guestSessionToken, itemUuid, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{itemUuid}")
    @Operation(
            summary = "Remove cart item",
            description = "Remove an item from cart. Uses soft delete."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart item removed successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Item doesn't belong to cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart item or customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> removeCartItem(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            @Parameter(description = "UUID of the cart item to remove", required = true)
            @PathVariable String itemUuid) {

        log.info("Remove cart item {}", itemUuid);

        CartResponseDTO cart = cartService.removeCartItem(customerUuid, guestSessionToken, itemUuid);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/promo")
    @Operation(
            summary = "Apply promo code",
            description = "Apply a discount promo code to cart. Validates promo code with Promotion module."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Promo code applied successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired promo code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> applyPromoCode(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            @Valid @RequestBody ApplyPromoCodeRequestDTO request) {

        log.info("Apply promo code: {}", request.getPromoCode());

        CartResponseDTO cart = cartService.applyPromoCode(customerUuid, guestSessionToken, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/promo")
    @Operation(
            summary = "Remove promo code",
            description = "Remove applied promo code from cart and recalculate totals."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Promo code removed successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> removePromoCode(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken) {

        log.info("Remove promo code");

        CartResponseDTO cart = cartService.removePromoCode(customerUuid, guestSessionToken);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    @Operation(
            summary = "Clear cart",
            description = "Remove all items from cart. Cart entity remains for future use."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Cart cleared successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> clearCart(
            @Parameter(description = "Customer UUID for authenticated users")
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken) {

        log.info("Clear cart - customerUuid: {}", customerUuid);

        cartService.clearCart(customerUuid, guestSessionToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    @Operation(
            summary = "Merge guest cart",
            description = "Merge guest cart to customer cart after login. Combines quantities for duplicate items and clears guest session."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Guest cart merged successfully",
                    content = @Content(schema = @Schema(implementation = CartResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Guest session token required or customer UUID missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CartResponseDTO> mergeGuestCart(
            @Parameter(description = "Customer UUID (required for merge)", required = true)
            @RequestHeader("X-Customer-UUID") String customerUuid,
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            HttpServletResponse response) {

        log.info("Merge guest cart to customer {}", customerUuid);

        if (guestSessionToken == null) {
            return ResponseEntity.badRequest().build();
        }

        CartResponseDTO cart = cartService.mergeGuestCartToCustomer(customerUuid, guestSessionToken);

        // Clear guest session cookie after merge
        removeGuestSessionCookie(response);

        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/session")
    @Operation(
            summary = "Clear guest session",
            description = "Clear guest session cookie (for testing and debugging)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Guest session cleared successfully"
            )
    })
    public ResponseEntity<Map<String, String>> clearGuestSession(
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken,
            HttpServletResponse response) {

        log.info("Clearing guest session - current token: {}", guestSessionToken);

        removeGuestSessionCookie(response);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Guest session cleared");
        result.put("previousToken", guestSessionToken != null ? guestSessionToken : "none");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/session")
    @Operation(
            summary = "Get guest session info",
            description = "Get current guest session token (for debugging)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Session info retrieved successfully"
            )
    })
    public ResponseEntity<Map<String, String>> getGuestSessionInfo(
            @Parameter(description = "Guest session token from cookie")
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionToken) {

        log.info("Getting guest session info - token: {}", guestSessionToken);

        Map<String, String> info = new HashMap<>();
        info.put("guestSessionToken", guestSessionToken != null ? guestSessionToken : "none");
        info.put("hasToken", String.valueOf(guestSessionToken != null));

        return ResponseEntity.ok(info);
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private void addGuestSessionCookie(HttpServletResponse response, String guestSessionToken) {
        Cookie cookie = new Cookie(GUEST_SESSION_COOKIE, guestSessionToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    private void removeGuestSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(GUEST_SESSION_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}

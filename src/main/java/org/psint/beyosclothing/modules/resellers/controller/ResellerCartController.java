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
import org.psint.beyosclothing.modules.resellers.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateCartItemRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerCartResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerCartService;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Reseller Cart Management
 */
@RestController
@RequestMapping("/api/v1/resellers/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Cart", description = "Cart management for resellers with price override support")
public class ResellerCartController {

    private final ResellerCartService cartService;
    private final ResellerSecurityService securityService;
    private final JwtUtil jwtUtil;

    /**
     * Get reseller cart with all items
     */
    @GetMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get cart", description = "Returns cart with all items, margins, and totals. Requires APPROVED status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerCartResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<ResellerCartResponse> getCart(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Fetching cart for reseller user ID: {}", userId);
        ResellerCartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Add item to cart
     */
    @PostMapping("/items")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Add item to cart",
               description = "Adds product to cart with optional price override. Validates stock and markup rules.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item added successfully",
                    content = @Content(schema = @Schema(implementation = ResellerCartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error - invalid product, insufficient stock, or invalid price override"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<ResellerCartResponse> addToCart(
            Authentication authentication,
            @Valid @RequestBody AddToCartRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Adding item to cart for reseller user ID: {} - Product: {}", userId, request.getProductUuid());
        ResellerCartResponse response = cartService.addToCart(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Update cart item
     */
    @PutMapping("/items/{cartItemUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Update cart item",
               description = "Updates quantity or price override for cart item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item updated successfully",
                    content = @Content(schema = @Schema(implementation = ResellerCartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Cart item not found")
    })
    public ResponseEntity<ResellerCartResponse> updateCartItem(
            Authentication authentication,
            @PathVariable String cartItemUuid,
            @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Updating cart item {} for reseller user ID: {}", cartItemUuid, userId);
        
        // Set the cart item UUID in the request
        request.setCartItemUuid(cartItemUuid);
        ResellerCartResponse response = cartService.updateCartItem(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Remove cart item
     */
    @DeleteMapping("/items/{cartItemUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Remove item from cart",
               description = "Removes a single item from cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item removed successfully",
                    content = @Content(schema = @Schema(implementation = ResellerCartResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Cart item not found")
    })
    public ResponseEntity<ResellerCartResponse> removeCartItem(
            Authentication authentication,
            @PathVariable String cartItemUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Removing cart item {} for reseller user ID: {}", cartItemUuid, userId);
        ResellerCartResponse response = cartService.removeCartItem(userId, cartItemUuid);
        return ResponseEntity.ok(response);
    }

    /**
     * Clear entire cart
     */
    @DeleteMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Clear cart",
               description = "Removes all items from cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Cart cleared successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("Clearing cart for reseller user ID: {}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get cart item count
     */
    @GetMapping("/count")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get cart item count", description = "Returns number of items in cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CartCountResponse> getCartItemCount(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);

        Long count = cartService.getCartItemCount(userId);
        CartCountResponse response = new CartCountResponse(count);
        return ResponseEntity.ok(response);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @Schema(description = "Cart item count response")
    public static class CartCountResponse {
        @Schema(description = "Number of items in cart", example = "5")
        private Long count;
    }
}

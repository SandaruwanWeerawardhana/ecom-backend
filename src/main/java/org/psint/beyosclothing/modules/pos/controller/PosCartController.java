package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.pos.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdateCartItemRequest;
import org.psint.beyosclothing.modules.pos.dto.request.ApplyTaxRequest;
import org.psint.beyosclothing.modules.pos.dto.request.ApplyDiscountRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosDraftResponse;
import org.psint.beyosclothing.modules.pos.service.PosCartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/carts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POS Carts", description = "Endpoints for POS cart operations")
public class PosCartController {

    private final PosCartService cartService;

    public static final String HEADER_TERMINAL = "X-Terminal-UUID";
    public static final String HEADER_CASHIER = "X-Cashier-UUID";

    @GetMapping("/terminal/{terminalUuid}")
    @PreAuthorize("hasAuthority('VIEW_POS')")
    @Operation(summary = "Get or create active cart for a terminal")
    public ResponseEntity<APIResponse<PosCartResponse>> getOrCreateCart(@PathVariable String terminalUuid,
                                                                       @RequestHeader(HEADER_TERMINAL) String terminalHeader,
                                                                       @RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                        @RequestParam(required = false) Boolean isDraft) {
        log.info("Terminal Header Only {}", terminalHeader);
        log.info("getOrCreateCart called. pathTerminal={}, headerTerminal={}, cashier={}", terminalUuid, terminalHeader, cashierHeader);
        if (!terminalUuid.equals(terminalHeader)) {
            log.warn("Terminal header UUID mismatch: path {} vs header {}", terminalUuid, terminalHeader);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(APIResponse.<PosCartResponse>builder()
                    .responseCode(ResponseCode.BAD_REQUEST.getCode())
                    .success(false)
                    .message("Terminal header UUID mismatch")
                    .build());
        }

        PosCartResponse resp = cartService.getOrCreateActiveCartForTerminal(terminalUuid, cashierHeader, isDraft);
        log.info("Cart retrieved for terminal={}, cashier={}, cartUuid={}", terminalUuid, cashierHeader, resp != null ? resp.getUuid() : "null");
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cart retrieved")
                .data(resp)
                .build());
    }

    @PostMapping({
            "/{cartUuid}/customers/{customerUuid}/items",
            "/{cartUuid}/items",
            "/customers/{customerUuid}/items",
            "/items"
    })
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Add item to cart")
    public ResponseEntity<APIResponse<PosCartResponse>> addItem(
            @PathVariable(name = "cartUuid", required = false) String cartUuid,
            @PathVariable(name = "customerUuid", required = false) String customerUuid,
            @RequestHeader(HEADER_TERMINAL) String terminalUuid,
            @RequestHeader(name = HEADER_CASHIER, required = false) String cashierHeader,
            @Valid @RequestBody AddToCartRequest request) {

        log.debug("addItem called. cart={}, terminal={}, cashier={}, customer={}, productId={}, variantId={}, qty={}",
                  cartUuid, terminalUuid, cashierHeader, customerUuid, request.getProductUuid(), request.getVariantUuid(), request.getQuantity());

        PosCartResponse resp = cartService.addItemToCart(cartUuid, terminalUuid, customerUuid, cashierHeader, request);
        log.info("Item added to cart={}, cashier={}, customer={}, newTotal={}",
                 resp != null ? resp.getUuid() : "null", cashierHeader, customerUuid, resp != null ? resp.getTotal() : "null");
        return ResponseEntity.status(HttpStatus.OK).body(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Item added")
                .data(resp)
                .build());
    }

    @PutMapping("/{cartUuid}/items/{itemUuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Update cart item quantity")
    public ResponseEntity<APIResponse<PosCartResponse>> updateItem(@PathVariable("cartUuid") String cartUuid,
                                                                   @PathVariable("itemUuid") String itemUuid,
                                                                   @RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                   @Valid @RequestBody UpdateCartItemRequest request) {
        log.debug("updateItem called. cart={}, item={}, cashier={}, qty={}", cartUuid, itemUuid, cashierHeader, request.getQuantity());
        PosCartResponse resp = cartService.updateCartItemQuantity(cartUuid, itemUuid, request.getQuantity());
        log.info("Cart item updated cart={}, item={}, cashier={}", cartUuid, itemUuid, cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Item updated")
                .data(resp)
                .build());
    }

    @DeleteMapping("/{cartUuid}/items/{itemUuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<APIResponse<PosCartResponse>> removeItem(@PathVariable String cartUuid,
                                                                   @PathVariable String itemUuid,
                                                                   @RequestHeader(HEADER_CASHIER) String cashierHeader) {
        log.debug("removeItem called. cart={}, item={}, cashier={}", cartUuid, itemUuid, cashierHeader);
        PosCartResponse resp = cartService.removeCartItem(cartUuid, itemUuid);
        log.info("Item removed from cart={}, item={}, cashier={}", cartUuid, itemUuid, cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Item removed")
                .data(resp)
                .build());
    }

    @PostMapping("/{cartUuid}/tax")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Apply tax to cart")
    public ResponseEntity<APIResponse<PosCartResponse>> applyTax(@PathVariable String cartUuid,
                                                                 @RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                 @Valid @RequestBody ApplyTaxRequest request) {
        log.debug("applyTax called. cart={}, cashier={}, tax={}", cartUuid, cashierHeader, request.getTaxPercentage());
        PosCartResponse resp = cartService.applyTax(cartUuid, BigDecimal.valueOf(request.getTaxPercentage()));
        log.info("Tax applied cart={}, cashier={}, tax={}", cartUuid, cashierHeader, request.getTaxPercentage());
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Tax applied")
                .data(resp)
                .build());
    }

    @PostMapping("/{cartUuid}/discount")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Apply discount to cart")
    public ResponseEntity<APIResponse<PosCartResponse>> applyDiscount(@PathVariable String cartUuid,
                                                                      @RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                      @Valid @RequestBody ApplyDiscountRequest request) {
        log.debug("applyDiscount called. cart={}, cashier={}, discount={}", cartUuid, cashierHeader, request.getDiscountAmount());
        PosCartResponse resp = cartService.applyDiscount(cartUuid, BigDecimal.valueOf(request.getDiscountAmount()));
        log.info("Discount applied cart={}, cashier={}, discount={}", cartUuid, cashierHeader, request.getDiscountAmount());
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Discount applied")
                .data(resp)
                .build());
    }

    @DeleteMapping("/{cartUuid}/clear")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Clear all items from cart")
    public ResponseEntity<APIResponse<PosCartResponse>> clearCart(@PathVariable String cartUuid,
                                                                   @RequestHeader(HEADER_CASHIER) String cashierHeader) {
        log.debug("clearCart called. cart={}, cashier={}", cartUuid, cashierHeader);
        PosCartResponse resp = cartService.clearCart(cartUuid);
        log.info("Cart cleared cart={}, cashier={}", cartUuid, cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cart cleared")
                .data(resp)
                .build());
    }

    @DeleteMapping("/{cartUuid}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    @Operation(summary = "Cancel cart by setting isActive to false")
    public ResponseEntity<APIResponse<PosCartResponse>> cancelCart(@PathVariable String cartUuid,
                                                                    @RequestHeader(HEADER_TERMINAL) String terminalUuid,
                                                                    @RequestHeader(HEADER_CASHIER) String cashierHeader) {
        log.debug("cancelCart called. cart={}, terminal={}, cashier={}", cartUuid, terminalUuid, cashierHeader);
        PosCartResponse resp = cartService.cancelCart(cartUuid, terminalUuid);
        log.info("Cart cancelled cart={}, terminal={}, cashier={}", cartUuid, terminalUuid, cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosCartResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cart cancelled")
                .data(resp)
                .build());
    }

    @PostMapping("/{cartUuid}/draft")
    @Operation(summary = "Mark cart as draft and return the cart with isDraft=true")
    public ResponseEntity<APIResponse<List<PosCartResponse>>> markCartAsDraft(
            @PathVariable String cartUuid,
            @RequestParam(required = false) List<String> additionalCartUuids,
            @RequestHeader(HEADER_TERMINAL) String terminalUuid) {
        
        // Build list of cart UUIDs to mark as draft
        List<String> cartUuids = new java.util.ArrayList<>();
        cartUuids.add(cartUuid);
        if (additionalCartUuids != null) {
            cartUuids.addAll(additionalCartUuids);
        }
        
        log.debug("markCartAsDraft called. carts={}, terminal={}", cartUuids, terminalUuid);
        List<PosCartResponse> responses = cartService.markCartAsDraft(cartUuids, terminalUuid);
        log.info("Cart(s) marked as draft count={}, terminal={}", responses.size(), terminalUuid);
        return ResponseEntity.ok(APIResponse.<List<PosCartResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cart(s) marked as draft")
                .data(responses)
                .build());
    }
}

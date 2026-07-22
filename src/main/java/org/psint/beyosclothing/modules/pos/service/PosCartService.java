package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosDraftResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PosCartService {
    // New method per request: canonical name
    PosCartResponse getOrCreateCart(String terminalUuid, String cashierUuid, Boolean isDraft);

    // Backwards-compatible method kept
    PosCartResponse getOrCreateActiveCartForTerminal(String terminalUuid, String cashierUuid,Boolean isDraft);

    // Updated method signature to accept cashierUuid for cart creation
    PosCartResponse addItemToCart(String cartUuid, String terminalUuid, String customerUuid, String cashierUuid, AddToCartRequest request);

    // Existing legacy method kept for backward compatibility
    PosCartResponse addItemToCart(String cartUuid, String productUuid, Long variantId, Integer quantity, BigDecimal unitPrice, Integer stockAvailable);

    PosCartResponse updateCartItemQuantity(String cartUuid, String itemUuid, Integer quantity);

    PosCartResponse removeCartItem(String cartUuid, String itemUuid);

    PosCartResponse applyTax(String cartUuid, BigDecimal taxPercentage);

    PosCartResponse applyDiscount(String cartUuid, BigDecimal discountAmount);

    PosCartResponse clearCart(String cartUuid);

    PosCartResponse cancelCart(String cartUuid, String terminalUuid);

    List<PosCartResponse> markCartAsDraft(List<String> cartUuids, String terminalUuid);

}

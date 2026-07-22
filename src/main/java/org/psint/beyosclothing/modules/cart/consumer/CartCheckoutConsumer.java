package org.psint.beyosclothing.modules.cart.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupResponse;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckResponse;
import org.psint.beyosclothing.modules.cart.entity.CartEntity;
import org.psint.beyosclothing.modules.cart.entity.CartItemEntity;
import org.psint.beyosclothing.modules.cart.entity.GuestCartMappingEntity;
import org.psint.beyosclothing.modules.cart.repository.CartItemRepository;
import org.psint.beyosclothing.modules.cart.repository.CartRepository;
import org.psint.beyosclothing.modules.cart.repository.GuestCartMappingRepository;
import org.psint.beyosclothing.modules.cart.service.CrossModuleLookupService;
import org.psint.beyosclothing.shared.dto.CartItemsLookupRequest;
import org.psint.beyosclothing.shared.dto.CartItemsLookupResponse;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RabbitMQ Consumer for Cart Checkout Requests.
 * Handles requests from Payment module for checkout page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartCheckoutConsumer {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final GuestCartMappingRepository guestCartMappingRepository;
    private final CrossModuleLookupService crossModuleLookupService;

    @RabbitListener(queues = "${app.rabbitmq.queue.cart-checkout-request:${app.rabbitmq.queue.cart-items-checkout-request:cart.checkout.request.queue}}")
    @Transactional("cartTransactionManager")
    public CartItemsLookupResponse handleCartCheckoutRequest(CartItemsLookupRequest request) {
        log.info("Received cart checkout request - Request ID: {}, Customer UUID: {}, Guest Token: {}",
                request.getRequestId(),
                request.getCustomerUuid(),
                request.getGuestSessionToken() != null ? "***" : null);

        try {
            CartCandidates cartCandidates = resolveCandidateCarts(request);
            List<CartEntity> candidateCarts = cartCandidates.carts();

            if (candidateCarts.isEmpty()) {
                log.warn("Cart not found - Request ID: {}", request.getRequestId());
                return notFoundResponse(request);
            }

            CartSelection cartSelection = selectCartItemsForCheckout(candidateCarts, request.getSelectedItemUuids());
            CartEntity cart = cartSelection.cart();
            List<CartItemEntity> selectedItems = cartSelection.items();

            if (cart == null || selectedItems == null || selectedItems.isEmpty()) {
                log.warn("No checkout items found - Request ID: {}", request.getRequestId());
                return notFoundResponse(request);
            }

            attachGuestCartToCustomerIfNeeded(cart, cartCandidates.customerId(), request.getGuestSessionToken());

            List<CartItemsLookupResponse.CartItemInfo> itemInfos = selectedItems.stream()
                    .map(this::buildCartItemInfo)
                    .collect(Collectors.toList());

            BigDecimal subtotal = selectedItems.stream()
                    .map(CartItemEntity::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalWeight = itemInfos.stream()
                    .map(CartItemsLookupResponse.CartItemInfo::getTotalItemWeight)
                    .filter(weight -> weight != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            CartItemsLookupResponse response = CartItemsLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .cartId(cart.getId())
                    .cartUuid(cart.getUuid())
                    .items(itemInfos)
                    .promoCode(null)
                    .promoDiscount(cart.getPromoDiscount() != null ? cart.getPromoDiscount() : BigDecimal.ZERO)
                    .subtotal(subtotal)
                    .totalWeight(totalWeight)
                    .build();

            log.info("Cart checkout response prepared - Request ID: {}, Cart ID: {}, Items: {}, Subtotal: {}, Weight: {} kg",
                    request.getRequestId(), cart.getId(), itemInfos.size(), subtotal, totalWeight);

            return response;

        } catch (Exception e) {
            log.error("Error processing cart checkout request - Request ID: {}", request.getRequestId(), e);
            return notFoundResponse(request);
        }
    }

    private CartCandidates resolveCandidateCarts(CartItemsLookupRequest request) {
        List<CartEntity> candidateCarts = new ArrayList<>();
        Long customerId = null;

        if (request.getCustomerUuid() != null) {
            CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(request.getCustomerUuid());
            log.info("Customer lookup - UUID: {}, Found: {}",
                    request.getCustomerUuid(),
                    customerLookup != null ? customerLookup.getFound() : false);

            if (customerLookup != null && Boolean.TRUE.equals(customerLookup.getFound())) {
                customerId = customerLookup.getCustomerId();
                cartRepository.findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(customerId)
                        .ifPresent(cart -> {
                            candidateCarts.add(cart);
                            log.info("Found cart for customer - Cart ID: {}, Subtotal: {}", cart.getId(), cart.getSubtotal());
                        });
            }
        }

        if (request.getGuestSessionToken() != null) {
            log.info("Looking up guest cart for checkout request");
            Optional<GuestCartMappingEntity> guestMapping =
                    guestCartMappingRepository.findByGuestSessionToken(request.getGuestSessionToken());

            if (guestMapping.isPresent()) {
                cartRepository.findById(guestMapping.get().getCartId())
                        .filter(cart -> Boolean.TRUE.equals(cart.getIsActive()))
                        .ifPresent(cart -> {
                            boolean alreadyAdded = candidateCarts.stream()
                                    .anyMatch(candidate -> candidate.getId().equals(cart.getId()));
                            if (!alreadyAdded) {
                                candidateCarts.add(cart);
                            }
                            log.info("Found guest cart - Cart ID: {}, Subtotal: {}", cart.getId(), cart.getSubtotal());
                        });
            }
        }

        return new CartCandidates(candidateCarts, customerId);
    }

    private void attachGuestCartToCustomerIfNeeded(CartEntity cart, Long customerId, String guestSessionToken) {
        if (customerId == null || cart == null || cart.getCustomerId() != null) {
            return;
        }

        cart.setCustomerId(customerId);
        cartRepository.save(cart);

        log.info("Attached guest cart to customer during checkout - Cart ID: {}, Customer ID: {}, Guest Token Present: {}",
                cart.getId(), customerId, guestSessionToken != null);
    }

    private CartSelection selectCartItemsForCheckout(List<CartEntity> candidateCarts, List<String> selectedItemUuids) {
        boolean hasSelectedItems = selectedItemUuids != null && !selectedItemUuids.isEmpty();
        long selectedItemCount = hasSelectedItems ? selectedItemUuids.stream().distinct().count() : 0;
        CartSelection firstPartialMatch = null;

        for (CartEntity candidate : candidateCarts) {
            List<CartItemEntity> items = hasSelectedItems
                    ? cartItemRepository.findByCartIdAndUuidInAndIsActiveTrue(candidate.getId(), selectedItemUuids)
                    : cartItemRepository.findByCartIdAndIsActiveTrue(candidate.getId());

            if (items != null && !items.isEmpty()) {
                log.info("Selected checkout cart - Cart ID: {}, Items: {}, FilteredBySelection: {}",
                        candidate.getId(), items.size(), hasSelectedItems);

                if (!hasSelectedItems || items.size() == selectedItemCount) {
                    return new CartSelection(candidate, items);
                }

                if (firstPartialMatch == null) {
                    firstPartialMatch = new CartSelection(candidate, items);
                }
            }
        }

        if (firstPartialMatch != null) {
            log.warn("No candidate cart contained every selected item UUID. Returning first partial match - Cart ID: {}, Items: {}",
                    firstPartialMatch.cart().getId(), firstPartialMatch.items().size());
            return firstPartialMatch;
        }

        log.warn("No checkout items found in {} candidate cart(s). Selected item UUIDs: {}",
                candidateCarts.size(), hasSelectedItems ? selectedItemUuids : "ALL");
        return new CartSelection(null, List.of());
    }

    private CartItemsLookupResponse notFoundResponse(CartItemsLookupRequest request) {
        return CartItemsLookupResponse.builder()
                .requestId(request.getRequestId())
                .found(false)
                .build();
    }

    private CartItemsLookupResponse.CartItemInfo buildCartItemInfo(CartItemEntity item) {
        ProductDetailsLookupResponse productDetails = crossModuleLookupService.lookupProductDetailsById(
                item.getProductId(),
                item.getVariantId()
        );

        String productUuid = null;
        String productTitle = "Product " + item.getProductId();
        String variantUuid = null;
        String variantTitle = null;
        String imageUrl = null;
        BigDecimal itemWeight = BigDecimal.ZERO;

        if (productDetails != null && Boolean.TRUE.equals(productDetails.getFound())) {
            productUuid = productDetails.getProductUuid();
            productTitle = productDetails.getProductTitle();
            variantUuid = productDetails.getVariantUuid();
            variantTitle = productDetails.getVariantAttributeSummary();

            imageUrl = productDetails.getVariantId() != null && productDetails.getVariantThumbnailUrl() != null
                    ? productDetails.getVariantThumbnailUrl()
                    : productDetails.getThumbnailUrl();

            itemWeight = productDetails.getWeightKg() != null ? productDetails.getWeightKg() : BigDecimal.ZERO;

            log.debug("Product details found - Product: {}, Weight: {} kg", productTitle, itemWeight);
        } else {
            log.warn("Product details not found for cart item - Product ID: {}, Variant ID: {}",
                    item.getProductId(), item.getVariantId());
        }

        Integer availableStock = 0;
        StockCheckResponse stockCheck = crossModuleLookupService.checkStockAvailability(
                item.getProductId(),
                item.getVariantId(),
                item.getQuantity()
        );

        if (stockCheck != null && Boolean.TRUE.equals(stockCheck.getFound())) {
            availableStock = stockCheck.getAvailableStock();
            log.debug("Stock check - Product ID: {}, Variant ID: {}, Available: {}, Requested: {}",
                    item.getProductId(), item.getVariantId(), availableStock, item.getQuantity());
        } else {
            log.warn("Stock information not found for Product ID: {}, Variant ID: {}",
                    item.getProductId(), item.getVariantId());
        }

        BigDecimal unitPrice = item.getSalePriceSnapshot() != null
                ? item.getSalePriceSnapshot()
                : item.getPriceSnapshot();

        BigDecimal totalPrice = item.getLineTotal();
        BigDecimal totalItemWeight = itemWeight.multiply(new BigDecimal(item.getQuantity()));

        return CartItemsLookupResponse.CartItemInfo.builder()
                .itemUuid(item.getUuid())
                .productId(item.getProductId())
                .productUuid(productUuid)
                .productTitle(productTitle)
                .variantId(item.getVariantId())
                .variantUuid(variantUuid)
                .variantTitle(variantTitle)
                .quantity(item.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .itemWeight(itemWeight)
                .totalItemWeight(totalItemWeight)
                .imageUrl(imageUrl)
                .availableStock(availableStock)
                .build();
    }

    private record CartSelection(CartEntity cart, List<CartItemEntity> items) {
    }

    private record CartCandidates(List<CartEntity> carts, Long customerId) {
    }
}

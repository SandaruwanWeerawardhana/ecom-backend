package org.psint.beyosclothing.modules.cart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.cart.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.ApplyPromoCodeRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.UpdateCartItemRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.response.CartItemResponseDTO;
import org.psint.beyosclothing.modules.cart.dto.response.CartResponseDTO;
import org.psint.beyosclothing.modules.cart.dto.external.*;
import org.psint.beyosclothing.modules.cart.service.CrossModuleLookupService;
import org.psint.beyosclothing.modules.cart.entity.*;
import org.psint.beyosclothing.modules.cart.events.*;
import org.psint.beyosclothing.modules.cart.repository.*;
import org.psint.beyosclothing.modules.cart.service.CartService;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cart Service Implementation
 * Handles all cart operations with Redis caching and RabbitMQ event publishing
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("cartTransactionManager")
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartAppliedPromotionRepository cartAppliedPromotionRepository;
    private final CartEventRepository cartEventRepository;
    private final GuestCartMappingRepository guestCartMappingRepository;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    private final CrossModuleLookupService crossModuleLookupService;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    private static final String CART_CACHE_PREFIX = "cart:";
    private static final long CART_CACHE_TTL_HOURS = 24;
    private static final int GUEST_CART_EXPIRY_DAYS = 30;

    @Override
    public CartResponseDTO getCart(String customerUuid, String guestSessionToken) {
        log.debug("Getting cart - customerUuid: {}, guestSessionToken: {}", customerUuid, guestSessionToken);

        // Try to get from cache first
        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new ResourceNotFoundException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        String cacheKey = buildCacheKey(customerId, guestSessionToken);
        CartResponseDTO cachedCart = (CartResponseDTO) redisTemplate.opsForValue().get(cacheKey);
        if (cachedCart != null) {
            log.debug("Cart found in cache");
            return cachedCart;
        }

        // Get from database
        CartEntity cart = findOrCreateCart(customerId, guestSessionToken);
        CartResponseDTO response = buildCartResponse(cart);

        // Cache the result
        cacheCart(cacheKey, response);

        return response;
    }

    @Override
    public CartResponseDTO addToCart(String customerUuid, String guestSessionToken, AddToCartRequestDTO request) {
        log.info("Adding to cart - customerUuid: {}, productUuid: {}, variantUuid: {}, quantity: {}",
                customerUuid, request.getProductUuid(), request.getVariantUuid(), request.getQuantity());

        // ============================================================
        // STEP 1: Resolve Customer ID via RabbitMQ (Customer Module)
        // ============================================================
        Long customerId = null;
        if (customerUuid != null) {
            CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null) {
                throw new BadRequestException("Unable to verify customer. Please try again later.");
            }
            if (!customerLookup.getFound()) {
                throw new ResourceNotFoundException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
            log.debug("Resolved customer - UUID: {}, ID: {}", customerUuid, customerId);
        }

        // ============================================================
        // STEP 2: Validate Product via RabbitMQ (Product Module)
        // ============================================================
        ProductLookupResponse productLookup;
        if (request.getVariantUuid() != null) {
            productLookup = crossModuleLookupService.lookupProductVariantByUuid(
                    request.getProductUuid(), request.getVariantUuid());
        } else {
            productLookup = crossModuleLookupService.lookupProductByUuid(request.getProductUuid());
        }

        if (productLookup == null) {
            throw new BadRequestException("Unable to verify product. Please try again later.");
        }
        if (!productLookup.getFound()) {
            throw new ResourceNotFoundException("Product not found: " + request.getProductUuid());
        }

        // Validate product type: SIMPLE must not have variant, VARIABLE must have variant
        validateProductType(productLookup, request);

        Long productId = productLookup.getProductId();
        Long variantId = productLookup.getVariantId();

        // ============================================================
        // STEP 3: Check Stock Availability via RabbitMQ (Inventory Module)
        // ============================================================
        StockCheckResponse stockCheck = crossModuleLookupService.checkStockAvailability(
                productId, variantId, request.getQuantity());

        validateStockCheck(stockCheck, productId, variantId, request.getQuantity());

        // Validate price snapshot is available before proceeding
        validatePriceSnapshot(productLookup);

        // ============================================================
        // STEP 4: Find or Create Cart
        // ============================================================
        CartEntity cart;
        if (customerId != null) {
            final Long resolvedCustomerId = customerId;
            cart = cartRepository.findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(resolvedCustomerId)
                    .orElseGet(() -> createCustomerCart(resolvedCustomerId));

            if (guestSessionToken != null) {
                mergeGuestCartIfExists(cart, guestSessionToken);
            }
        } else if (guestSessionToken != null) {
            cart = findOrCreateCart(null, guestSessionToken);
        } else {
            throw new BadRequestException("Either customer UUID or guest session token must be provided");
        }

        // ============================================================
        // STEP 5: Add or Update Cart Item
        // ============================================================
        Optional<CartItemEntity> existingItem = findExistingCartItem(cart.getId(), productId, variantId);

        CartItemEntity cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            int newQuantity = cartItem.getQuantity() + request.getQuantity();

            StockCheckResponse newStockCheck = crossModuleLookupService.checkStockAvailability(
                    productId, variantId, newQuantity);

            if (newStockCheck == null || !newStockCheck.getFound()) {
                throw new BadRequestException("Unable to verify stock for updated quantity. Please try again.");
            }
            if (!newStockCheck.getIsAvailable() && !newStockCheck.getAllowBackorder()) {
                throw new BadRequestException(String.format(
                        "Not enough stock available. Current stock: %d, requested: %d",
                        newStockCheck.getAvailableStock(), newQuantity));
            }

            cartItem.setQuantity(newQuantity);
            cartItem.setStockAvailable(newStockCheck.getAvailableStock());
            cartItem = cartItemRepository.save(cartItem);
            log.info("Updated cart item - cartId: {}, productId: {}, newQty: {}",
                    cart.getId(), productId, newQuantity);
        } else {
            cartItem = createNewCartItem(cart.getId(), request, productLookup, stockCheck);
            cartItem = cartItemRepository.save(cartItem);
            log.info("Created new cart item - cartId: {}, productId: {}, variantId: {}, qty: {}",
                    cart.getId(), productId, variantId, request.getQuantity());
        }

        // ============================================================
        // STEP 6: Post-processing
        // ============================================================
        recalculateCartTotals(cart);

        logCartEvent(cart.getId(), "ITEM_ADDED", buildItemEventData(request));

        publishCartItemAddedEvent(cart, cartItem);

        invalidateCartCache(customerId, guestSessionToken);

        return buildCartResponse(cart);
    }

    @Override
    public CartResponseDTO updateCartItem(String customerUuid, String guestSessionToken,
                                          String itemUuid, UpdateCartItemRequestDTO request) {
        log.info("Updating cart item {} - quantity: {}", itemUuid, request.getQuantity());

        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new RuntimeException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        CartEntity cart = findCart(customerId, guestSessionToken);

        // Find cart item by UUID
        CartItemEntity cartItem = cartItemRepository.findByUuid(itemUuid)
                .orElseThrow(() -> new RuntimeException("Cart item not found: " + itemUuid));

        // Verify item belongs to this cart
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to this cart");
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        // Recalculate totals
        recalculateCartTotals(cart);

        // Clear cache
        invalidateCartCache(customerId, guestSessionToken);

        return buildCartResponse(cart);
    }

    @Override
    public CartResponseDTO removeCartItem(String customerUuid, String guestSessionToken, String itemUuid) {
        log.info("Removing cart item {}", itemUuid);

        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new RuntimeException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        CartEntity cart = findCart(customerId, guestSessionToken);

        // Find cart item by UUID
        CartItemEntity cartItem = cartItemRepository.findByUuid(itemUuid)
                .orElseThrow(() -> new RuntimeException("Cart item not found: " + itemUuid));

        // Verify item belongs to this cart
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to this cart");
        }

        // Soft delete
        cartItem.setIsActive(false);
        cartItemRepository.save(cartItem);

        // Log event
        logCartEvent(cart.getId(), "ITEM_REMOVED",
                String.format("{\"product_id\":%d}", cartItem.getProductId()));

        // Publish event
        publishCartItemRemovedEvent(cart, cartItem);

        // Recalculate totals
        recalculateCartTotals(cart);

        // Clear cache
        invalidateCartCache(customerId, guestSessionToken);

        return buildCartResponse(cart);
    }

    @Override
    public CartResponseDTO applyPromoCode(String customerUuid, String guestSessionToken,
                                          ApplyPromoCodeRequestDTO request) {
        log.info("Applying promo code: {}", request.getPromoCode());

        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new RuntimeException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        CartEntity cart = findCart(customerId, guestSessionToken);
        List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());

        // Build promo validation request - FETCH CATEGORY INFO FOR EACH ITEM
        List<PromoValidationRequest.CartItemInfo> cartItemInfos = items.stream()
                .map(item -> {
                    // Fetch product details to get category ID
                    ProductDetailsLookupResponse productDetails = crossModuleLookupService.lookupProductDetailsById(
                            item.getProductId(), item.getVariantId());
                    
                    Long categoryId = null;
                    if (productDetails != null && productDetails.getFound()) {
                        categoryId = productDetails.getCategoryId();
                    }
                    
                    return PromoValidationRequest.CartItemInfo.builder()
                            .productId(item.getProductId())
                            .variantId(item.getVariantId())
                            .categoryId(categoryId) // ✅ Now includes category ID
                            .quantity(item.getQuantity())
                            .price(item.getPriceSnapshot())
                            .build();
                })
                .collect(Collectors.toList());

        PromoValidationRequest validationRequest = PromoValidationRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .promoCode(request.getPromoCode())
                .customerId(customerId)
                .cartSubtotal(cart.getSubtotal())
                .cartItems(cartItemInfos)
                .build();

        // Validate promo code with Promotion module
        PromoValidationResponse validationResponse = crossModuleLookupService.validatePromoCode(validationRequest);

        if (!validationResponse.getIsValid()) {
            throw new RuntimeException("Invalid promo code: " +
                    (validationResponse.getErrorMessage() != null ? validationResponse.getErrorMessage() : "Unknown error"));
        }

        // Apply promo code
        cart.setPromoCodeId(validationResponse.getPromotionId());
        cart.setPromoDiscount(validationResponse.getCalculatedDiscount());
        cartRepository.save(cart);

        // Recalculate totals
        recalculateCartTotals(cart);

        // Log event
        logCartEvent(cart.getId(), "PROMO_APPLIED",
                String.format("{\"promo_code\":\"%s\",\"discount\":%s}",
                        request.getPromoCode(), validationResponse.getCalculatedDiscount()));

        // Publish event
        publishPromoAppliedEvent(cart, validationResponse);

        // Clear cache
        invalidateCartCache(customerId, guestSessionToken);

        return buildCartResponse(cart);
    }


    @Override
    public CartResponseDTO removePromoCode(String customerUuid, String guestSessionToken) {
        log.info("Removing promo code");

        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new RuntimeException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        CartEntity cart = findCart(customerId, guestSessionToken);

        cart.setPromoCodeId(null);
        cart.setPromoDiscount(BigDecimal.ZERO);
        cartRepository.save(cart);

        // Recalculate totals
        recalculateCartTotals(cart);

        // Clear cache
        invalidateCartCache(customerId, guestSessionToken);

        return buildCartResponse(cart);
    }

    @Override
    public void clearCart(String customerUuid, String guestSessionToken) {
        log.info("Clearing cart - customerUuid: {}", customerUuid);

        // Lookup customer ID from UUID if provided
        Long customerId = null;
        if (customerUuid != null) {
            org.psint.beyosclothing.shared.dto.CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            if (customerLookup == null || !customerLookup.getFound()) {
                throw new RuntimeException("Customer not found: " + customerUuid);
            }
            customerId = customerLookup.getCustomerId();
        }

        CartEntity cart = findCart(customerId, guestSessionToken);

        // Soft delete all items
        List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());
        items.forEach(item -> item.setIsActive(false));
        cartItemRepository.saveAll(items);

        // Reset cart totals
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDiscountTotal(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cartRepository.save(cart);

        // Clear cache
        invalidateCartCache(customerId, guestSessionToken);
    }

    @Override
    public CartResponseDTO mergeGuestCartToCustomer(String customerUuid, String guestSessionToken) {
        log.info("Merging guest cart to customer {}", customerUuid);

        // Lookup customer ID from UUID
        if (customerUuid == null) {
            throw new RuntimeException("Customer UUID is required for cart merge");
        }

        CustomerLookupResponse customerLookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
        if (customerLookup == null || !customerLookup.getFound()) {
            throw new RuntimeException("Customer not found: " + customerUuid);
        }
        Long customerId = customerLookup.getCustomerId();

        // Get guest cart
        Optional<GuestCartMappingEntity> guestMapping =
                guestCartMappingRepository.findByGuestSessionToken(guestSessionToken);

        if (guestMapping.isEmpty()) {
            log.debug("No guest cart found to merge");
            return getCart(customerUuid, null);
        }

        CartEntity guestCart = cartRepository.findById(guestMapping.get().getCartId())
                .orElseThrow(() -> new RuntimeException("Guest cart not found"));

        // Get or create customer cart
        Optional<CartEntity> customerCartOpt = cartRepository.findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(customerId);
        CartEntity customerCart = customerCartOpt.orElseGet(() -> createCustomerCart(customerId));

        // Merge items
        List<CartItemEntity> guestItems = cartItemRepository.findByCartIdAndIsActiveTrue(guestCart.getId());
        int mergedCount = 0;

        for (CartItemEntity guestItem : guestItems) {
            Optional<CartItemEntity> existingItem = findExistingCartItem(
                    customerCart.getId(), guestItem.getProductId(), guestItem.getVariantId());

            if (existingItem.isPresent()) {
                // Merge quantities
                CartItemEntity item = existingItem.get();
                item.setQuantity(item.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(item);
            } else {
                // Move item to customer cart
                guestItem.setCartId(customerCart.getId());
                cartItemRepository.save(guestItem);
            }
            mergedCount++;
        }

        // Mark guest cart as inactive
        guestCart.setIsActive(false);
        cartRepository.save(guestCart);

        // Mark mapping as merged
        GuestCartMappingEntity mapping = guestMapping.get();
        mapping.setIsMerged(true);
        guestCartMappingRepository.save(mapping);

        // Recalculate customer cart totals
        recalculateCartTotals(customerCart);

        // Log event
        logCartEvent(customerCart.getId(), "CART_MERGED_AFTER_LOGIN",
                String.format("{\"guest_cart\":\"%s\",\"customer_id\":%d,\"items_merged\":%d}",
                        guestCart.getUuid(), customerId, mergedCount));

        // Publish event
        publishCartMergedEvent(guestCart, customerCart, mergedCount);

        // Clear both caches
        invalidateCartCache(customerId, null);
        invalidateCartCache(null, guestSessionToken);

        return buildCartResponse(customerCart);
    }

    @Override
    public String generateGuestSessionToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void expireOldGuestCarts() {
        log.info("Running guest cart expiration job");

        LocalDateTime expirationDate = LocalDateTime.now().minusDays(GUEST_CART_EXPIRY_DAYS);
        List<CartEntity> expiredCarts = cartRepository.findExpiredCarts(expirationDate);

        for (CartEntity cart : expiredCarts) {
            cart.setIsActive(false);
            cartRepository.save(cart);

            logCartEvent(cart.getId(), "CART_EXPIRED", null);
            publishCartExpiredEvent(cart);
        }

        log.info("Expired {} guest carts", expiredCarts.size());
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private CartEntity findOrCreateCart(Long customerId, String guestSessionToken) {
        if (customerId != null) {
            return cartRepository.findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(customerId)
                    .orElseGet(() -> createCustomerCart(customerId));
        } else if (guestSessionToken != null) {
            Optional<GuestCartMappingEntity> mapping =
                    guestCartMappingRepository.findByGuestSessionToken(guestSessionToken);

            if (mapping.isPresent()) {
                return cartRepository.findById(mapping.get().getCartId())
                        .orElseThrow(() -> new RuntimeException("Cart not found"));
            } else {
                return createGuestCart(guestSessionToken);
            }
        }

        throw new RuntimeException("Either customerId or guestSessionToken must be provided");
    }

    private CartEntity findCart(Long customerId, String guestSessionToken) {
        if (customerId != null) {
            return cartRepository.findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(customerId)
                    .orElseThrow(() -> new RuntimeException("Cart not found"));
        } else if (guestSessionToken != null) {
            GuestCartMappingEntity mapping = guestCartMappingRepository
                    .findByGuestSessionToken(guestSessionToken)
                    .orElseThrow(() -> new RuntimeException("Guest cart not found"));

            return cartRepository.findById(mapping.getCartId())
                    .orElseThrow(() -> new RuntimeException("Cart not found"));
        }

        throw new RuntimeException("Either customerId or guestSessionToken must be provided");
    }

    private CartEntity createCustomerCart(Long customerId) {
        CartEntity cart = CartEntity.builder()
                .customerId(customerId)
                .subtotal(BigDecimal.ZERO)
                .discountTotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

        return cartRepository.save(cart);
    }

    private CartEntity createGuestCart(String guestSessionToken) {
        String guestId = UUID.randomUUID().toString();

        CartEntity cart = CartEntity.builder()
                .guestId(guestId)
                .subtotal(BigDecimal.ZERO)
                .discountTotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .expiresAt(LocalDateTime.now().plusDays(GUEST_CART_EXPIRY_DAYS))
                .build();

        cart = cartRepository.save(cart);

        // Create guest mapping
        GuestCartMappingEntity mapping = GuestCartMappingEntity.builder()
                .guestSessionToken(guestSessionToken)
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .isMerged(false)
                .build();

        guestCartMappingRepository.save(mapping);

        return cart;
    }

    private Optional<CartItemEntity> findExistingCartItem(Long cartId, Long productId, Long variantId) {
        if (variantId != null) {
            return cartItemRepository.findByCartIdAndProductIdAndVariantIdAndIsActiveTrue(
                    cartId, productId, variantId);
        } else {
            return cartItemRepository.findByCartIdAndProductIdAndVariantIdIsNullAndIsActiveTrue(
                    cartId, productId);
        }
    }

    /**
     * Validate product type constraints:
     * - SIMPLE product: ignore any variantUuid (stock tracked with variantId=null)
     * - VARIABLE product: must have variantUuid and resolved variantId
     */
    private void validateProductType(ProductLookupResponse productLookup, AddToCartRequestDTO request) {
        String productType = productLookup.getProductType();

        if ("VARIABLE".equals(productType)) {
            if (request.getVariantUuid() == null || request.getVariantUuid().isBlank()) {
                throw new BadRequestException("Variant is required for variable product: " + request.getProductUuid());
            }
            if (productLookup.getVariantId() == null) {
                throw new ResourceNotFoundException("Variant not found: " + request.getVariantUuid());
            }
        } else if ("SIMPLE".equals(productType)) {
            // SIMPLE products: stock is tracked with variantId=null.
            // Ignore any variantUuid sent by the frontend.
            log.debug("SIMPLE product detected - ignoring variantUuid if present, using product-level stock");
        }
    }

    /**
     * Validate stock check response from Inventory module.
     * Handles: null response, record not found, out of stock.
     */
    private void validateStockCheck(StockCheckResponse stockCheck, Long productId, Long variantId, Integer quantity) {
        if (stockCheck == null) {
            throw new BadRequestException("Unable to check stock availability. Please try again later.");
        }
        if (!stockCheck.getFound()) {
            throw new BadRequestException(String.format(
                    "Stock information not found for product ID: %d%s",
                    productId,
                    variantId != null ? ", variant ID: " + variantId : ""));
        }
        if (!stockCheck.getIsAvailable() && !stockCheck.getAllowBackorder()) {
            throw new BadRequestException(String.format(
                    "Product is out of stock. Available: %d, requested: %d",
                    stockCheck.getAvailableStock(), quantity));
        }
    }

    /**
     * Validate that a valid price snapshot is available.
     * Prevents saving cart items with null price which breaks line total calculation.
     */
    private void validatePriceSnapshot(ProductLookupResponse productLookup) {
        BigDecimal price;
        if (productLookup.getVariantId() != null && productLookup.getVariantShowcasePrice() != null) {
            price = productLookup.getVariantShowcasePrice();
        } else {
            price = productLookup.getShowcasePrice();
        }
        if (price == null) {
            throw new BadRequestException("Product price is not available. Please contact support.");
        }
    }

    /**
     * Merge existing guest cart items into customer cart.
     * Called when a customer adds to cart while having a leftover guest session.
     * - Existing items: merge quantities
     * - New items: move to customer cart
     * - Deactivates guest cart and mapping after merge
     */
    private void mergeGuestCartIfExists(CartEntity customerCart, String guestSessionToken) {
        Optional<GuestCartMappingEntity> guestMapping =
                guestCartMappingRepository.findByGuestSessionToken(guestSessionToken);

        if (guestMapping.isEmpty() || guestMapping.get().getIsMerged()) {
            return;
        }

        Optional<CartEntity> guestCartOpt = cartRepository.findById(guestMapping.get().getCartId());
        if (guestCartOpt.isEmpty() || !guestCartOpt.get().getIsActive()) {
            return;
        }

        CartEntity guestCart = guestCartOpt.get();

        // Skip if same cart (edge case)
        if (guestCart.getId().equals(customerCart.getId())) {
            return;
        }

        List<CartItemEntity> guestItems = cartItemRepository.findByCartIdAndIsActiveTrue(guestCart.getId());
        if (guestItems.isEmpty()) {
            return;
        }

        int mergedCount = 0;
        for (CartItemEntity guestItem : guestItems) {
            Optional<CartItemEntity> existingItem = findExistingCartItem(
                    customerCart.getId(), guestItem.getProductId(), guestItem.getVariantId());

            if (existingItem.isPresent()) {
                // Merge quantities for duplicate items
                CartItemEntity item = existingItem.get();
                item.setQuantity(item.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(item);
            } else {
                // Move item to customer cart
                guestItem.setCartId(customerCart.getId());
                cartItemRepository.save(guestItem);
            }
            mergedCount++;
        }

        // Deactivate guest cart and mark mapping as merged
        guestCart.setIsActive(false);
        cartRepository.save(guestCart);

        GuestCartMappingEntity mapping = guestMapping.get();
        mapping.setIsMerged(true);
        guestCartMappingRepository.save(mapping);

        // Recalculate customer cart totals after merge
        recalculateCartTotals(customerCart);

        logCartEvent(customerCart.getId(), "GUEST_CART_AUTO_MERGED",
                String.format("{\"guest_cart_uuid\":\"%s\",\"items_merged\":%d}",
                        guestCart.getUuid(), mergedCount));

        publishCartMergedEvent(guestCart, customerCart, mergedCount);

        // Invalidate guest cart cache
        invalidateCartCache(null, guestSessionToken);

        log.info("Auto-merged {} guest items from cart {} into customer cart {}",
                mergedCount, guestCart.getUuid(), customerCart.getUuid());
    }

    private CartItemEntity createNewCartItem(Long cartId, AddToCartRequestDTO request,
                                             ProductLookupResponse productLookup, StockCheckResponse stockCheck) {
        // Use actual price - prefer variant price over product price
        BigDecimal price = productLookup.getVariantId() != null && productLookup.getVariantShowcasePrice() != null
                ? productLookup.getVariantShowcasePrice()
                : productLookup.getShowcasePrice();

        BigDecimal salePrice = productLookup.getVariantId() != null && productLookup.getVariantSalePrice() != null
                ? productLookup.getVariantSalePrice()
                : productLookup.getSalePrice();

        return CartItemEntity.builder()
                .cartId(cartId)
                .productId(productLookup.getProductId())
                .variantId(productLookup.getVariantId())
                .quantity(request.getQuantity())
                .priceSnapshot(price)
                .salePriceSnapshot(salePrice)
                .stockAvailable(stockCheck.getAvailableStock())
                .build();
    }


    private void recalculateCartTotals(CartEntity cart) {
        List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());

        BigDecimal subtotal = items.stream()
                .map(CartItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);
        cart.setTotal(subtotal.subtract(cart.getPromoDiscount() != null ? cart.getPromoDiscount() : BigDecimal.ZERO));

        cartRepository.save(cart);
    }

    private CartResponseDTO buildCartResponse(CartEntity cart) {
        List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());

        List<CartItemResponseDTO> itemDTOs = items.stream()
                .map(this::buildCartItemResponse)
                .collect(Collectors.toList());

        return CartResponseDTO.builder()
                .id(cart.getId())
                .uuid(cart.getUuid())
                .customerId(cart.getCustomerId())
                .guestId(cart.getGuestId())
                .items(itemDTOs)
                .itemCount(items.size())
                .subtotal(cart.getSubtotal())
                .discountTotal(cart.getDiscountTotal())
                .total(cart.getTotal())
                .promoDiscount(cart.getPromoDiscount())
                .hasPromo(cart.getPromoCodeId() != null)
                .expiresAt(cart.getExpiresAt())
                .dateCreated(cart.getDateCreated())
                .dateUpdated(cart.getDateUpdated())
                .build();
    }

    private CartItemResponseDTO buildCartItemResponse(CartItemEntity item) {
        // Fetch product details from Product module using cross-module lookup
        ProductDetailsLookupResponse productDetails = crossModuleLookupService.lookupProductDetailsById(
                item.getProductId(), 
                item.getVariantId()
        );

        // If product not found, use fallback placeholder data
        String productTitle = "Product " + item.getProductId(); // Default
        String thumbnailUrl = null;
        String variantSummary = null;
        String sku = null;
        
        if (productDetails != null && productDetails.getFound()) {
            productTitle = productDetails.getProductTitle();
            thumbnailUrl = productDetails.getVariantId() != null && productDetails.getVariantThumbnailUrl() != null
                    ? productDetails.getVariantThumbnailUrl()
                    : productDetails.getThumbnailUrl();
            variantSummary = productDetails.getVariantAttributeSummary();
            sku = productDetails.getVariantId() != null && productDetails.getVariantSku() != null
                    ? productDetails.getVariantSku()
                    : productDetails.getProductSku();
        } else {
            log.warn("Product details not found for cart item - Product ID: {}, Variant ID: {}", 
                    item.getProductId(), item.getVariantId());
        }

        return CartItemResponseDTO.builder()
                .id(item.getId())
                .uuid(item.getUuid())
                .productId(item.getProductId())
                .productTitle(productTitle)
                .productSku(sku)
                .thumbnailUrl(thumbnailUrl)
                .variantId(item.getVariantId())
                .variantAttributeSummary(variantSummary)
                .quantity(item.getQuantity())
                .price(item.getPriceSnapshot())
                .salePrice(item.getSalePriceSnapshot())
                .lineTotal(item.getLineTotal())
                .stockAvailable(item.getStockAvailable())
                .isOnSale(item.isOnSale())
                .isAvailable(productDetails != null && productDetails.getIsActive())
                .build();
    }

    private void logCartEvent(Long cartId, String eventType, String eventData) {
        CartEventEntity event = CartEventEntity.builder()
                .cartId(cartId)
                .eventType(eventType)
                .eventData(eventData)
                .build();

        cartEventRepository.save(event);
    }

    private String buildItemEventData(AddToCartRequestDTO request) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("product_uuid", request.getProductUuid());
            data.put("variant_uuid", request.getVariantUuid());
            data.put("quantity", request.getQuantity());
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }


    private void publishCartItemAddedEvent(CartEntity cart, CartItemEntity item) {
        CartItemAddedEvent event = CartItemAddedEvent.builder()
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .customerId(cart.getCustomerId())
                .guestId(cart.getGuestId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .price(item.getPriceSnapshot())
                .addedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(cartExchange, "cart.item.added", event);
    }

    private void publishCartItemRemovedEvent(CartEntity cart, CartItemEntity item) {
        CartItemRemovedEvent event = CartItemRemovedEvent.builder()
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .customerId(cart.getCustomerId())
                .guestId(cart.getGuestId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .removedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(cartExchange, "cart.item.removed", event);
    }

    private void publishCartMergedEvent(CartEntity guestCart, CartEntity customerCart, int itemsMerged) {
        CartMergedAfterLoginEvent event = CartMergedAfterLoginEvent.builder()
                .guestCartId(guestCart.getId())
                .guestCartUuid(guestCart.getUuid())
                .guestId(guestCart.getGuestId())
                .customerCartId(customerCart.getId())
                .customerCartUuid(customerCart.getUuid())
                .customerId(customerCart.getCustomerId())
                .itemsMerged(itemsMerged)
                .mergedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(cartExchange, "cart.merged", event);
    }

    private void publishPromoAppliedEvent(CartEntity cart, PromoValidationResponse validationResponse) {
        CartPromoAppliedEvent event = CartPromoAppliedEvent.builder()
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .customerId(cart.getCustomerId())
                .guestId(cart.getGuestId())
                .promoCodeId(validationResponse.getPromotionId())
                .promoCode(validationResponse.getPromoCode())
                .discountAmount(validationResponse.getCalculatedDiscount())
                .appliedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(cartExchange, "cart.promo.applied", event);
    }


    private void publishCartExpiredEvent(CartEntity cart) {
        CartExpiredEvent event = CartExpiredEvent.builder()
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .guestId(cart.getGuestId())
                .customerId(cart.getCustomerId())
                .expiredAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(cartExchange, "cart.expired", event);
    }

    private String buildCacheKey(Long customerId, String guestSessionToken) {
        if (customerId != null) {
            return CART_CACHE_PREFIX + "customer:" + customerId;
        } else if (guestSessionToken != null) {
            return CART_CACHE_PREFIX + "guest:" + guestSessionToken;
        }
        throw new RuntimeException("Invalid cache key parameters");
    }

    private void cacheCart(String cacheKey, CartResponseDTO cart) {
        redisTemplate.opsForValue().set(cacheKey, cart, CART_CACHE_TTL_HOURS, TimeUnit.HOURS);
    }

    private void invalidateCartCache(Long customerId, String guestSessionToken) {
        String cacheKey = buildCacheKey(customerId, guestSessionToken);
        redisTemplate.delete(cacheKey);
    }
}


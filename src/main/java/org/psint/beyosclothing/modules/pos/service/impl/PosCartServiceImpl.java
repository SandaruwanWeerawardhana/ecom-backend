package org.psint.beyosclothing.modules.pos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupRequest;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupResponse;
import org.psint.beyosclothing.modules.pos.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartItemResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosDraftResponse;
import org.psint.beyosclothing.modules.pos.entity.*;
import org.psint.beyosclothing.modules.pos.exception.CartNotFoundException;
import org.psint.beyosclothing.modules.pos.exception.InsufficientStockException;
import org.psint.beyosclothing.modules.pos.exception.ProductNotFoundException;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCashierRepository;
import org.psint.beyosclothing.modules.pos.service.PosCartService;
import org.psint.beyosclothing.modules.pos.service.PosStockValidationService;
import org.psint.beyosclothing.modules.pos.service.PosVariantLookupService;
import org.psint.beyosclothing.modules.pos.service.PosCustomerLookupService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;
import org.psint.beyosclothing.modules.cart.service.CrossModuleLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosCartServiceImpl implements PosCartService {

    private final PosCartRepository cartRepository;
    private final PosCartItemRepository itemRepository;
    private final PosProductCacheRepository productCacheRepository;
    private final PosTerminalRepository terminalRepository;
    private final PosCashierRepository cashierRepository;
    private final PosStockValidationService stockValidationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PosCartCacheService cartCacheService;
    private final PosVariantLookupService variantLookupService;
    private final PosCustomerLookupService customerLookupService;
    private final CrossModuleLookupService crossModuleLookupService;
    private final org.psint.beyosclothing.modules.pos.service.PosCustomerService posCustomerService;
    private final org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository posCustomerRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderSmsNotificationService smsNotificationService;

    @Value("${app.rabbitmq.exchange.product:product.exchange}")
    private String productExchange;

    private static final String CART_CACHE_PREFIX = "pos:cart:terminal:";
    private static final long CART_CACHE_TTL_SECONDS = TimeUnit.HOURS.toSeconds(4);

    @Override
    public PosCartResponse getOrCreateCart(String terminalUuid, String cashierUuid, Boolean isDraft) {
        return getOrCreateActiveCartForTerminal(terminalUuid, cashierUuid,isDraft);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse getOrCreateActiveCartForTerminal(String terminalUuid, String cashierUuid,Boolean isDraft) {
        PosTerminalEntity terminal = terminalRepository.findByUuid(terminalUuid)
                .orElseThrow(() -> new CartNotFoundException("Terminal not found or inactive: " + terminalUuid));
        if (terminal.getIsActive() == null || !terminal.getIsActive()) {
            throw new CartNotFoundException("Terminal is not active: " + terminalUuid);
        }

        PosCashierEntity cashier = cashierRepository.findByUuid(cashierUuid)
                .orElseThrow(() -> new CartNotFoundException("Cashier not found or inactive: " + cashierUuid));
        if (cashier.getIsActive() == null || !cashier.getIsActive()) {
            throw new CartNotFoundException("Cashier is not active: " + cashierUuid);
        }

        Optional<PosCartResponse> cached = cartCacheService.loadCartFromCache(terminalUuid);
        if (cached.isPresent()) {
            PosCartResponse cachedResp = cached.get();
            
            // Only return cached cart if it's active
            if (cachedResp.getIsActive() == null || !cachedResp.getIsActive()) {
                log.debug("Ignoring cached cart for terminal {} because it's not active", terminalUuid);
                cartCacheService.removeCacheForTerminal(terminalUuid);
            } else {
                // If caller specified isDraft, only return cached cart when it matches the requested draft flag
                if (isDraft != null) {
                    if (isDraft && Boolean.TRUE.equals(cachedResp.getIsDraft())) {
                        return cachedResp;
                    }
                    if (!isDraft && !Boolean.TRUE.equals(cachedResp.getIsDraft())) {
                        return cachedResp;
                    }
                    // cached value doesn't match requested draft flag -> ignore cached
                    log.debug("Ignoring cached cart for terminal {} because it doesn't match requested isDraft={}", terminalUuid, isDraft);
                } else {
                    // Default behavior: return non-draft cached cart only
                    if (!Boolean.TRUE.equals(cachedResp.getIsDraft())) {
                        return cachedResp;
                    }
                    log.debug("Ignoring cached draft cart for terminal {}", terminalUuid);
                }
            }
        }

        // Look for existing active carts in DB (ordered newest first). Pick the first that matches the requested isDraft flag.
        List<PosCartEntity> existingCarts = cartRepository.findByTerminalIdAndIsActiveTrueOrderByUpdatedAtDesc(terminal.getId());
        if (existingCarts != null && !existingCarts.isEmpty()) {
            for (PosCartEntity existing : existingCarts) {
                Boolean existingDraft = existing.getIsDraft();
                boolean match;
                if (isDraft != null) {
                    match = (isDraft && Boolean.TRUE.equals(existingDraft)) || (!isDraft && !Boolean.TRUE.equals(existingDraft));
                } else {
                    match = !Boolean.TRUE.equals(existingDraft);
                }

                if (match) {
                    PosCartResponse resp = mapToResponse(existing);
                    cartCacheService.cacheFullCart(terminalUuid, resp);
                    return resp;
                }
            }
            log.debug("No existing active cart matched requested isDraft={} for terminal {}. Will create a new active cart.", isDraft, terminalUuid);
        }

        PosCartEntity cart = PosCartEntity.builder()
                .terminalId(terminal.getId())
                .cashierId(cashier.getId())
                .isActive(true)
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .taxPercentage(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

        cart = cartRepository.save(cart);

        PosCartResponse resp = mapToResponse(cart);
        cartCacheService.cacheFullCart(terminalUuid, resp);

        return resp;
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse addItemToCart(String cartUuid, String terminalUuid, String customerUuid, String cashierUuid, AddToCartRequest request) {

        log.debug("addItemToCart - cartUuid={}, terminalUuid={}, customerUuid={}, cashierUuid={}",
                cartUuid, terminalUuid, customerUuid, cashierUuid);

        final PosCartEntity cart;
        final String finalCartUuid;

        // If cartUuid is null, create a new cart for this terminal
        if (cartUuid == null || cartUuid.trim().isEmpty()) {
            log.info("No cartUuid provided, creating new cart for terminal={}", terminalUuid);

            // Validate terminal exists and is active
            PosTerminalEntity terminal = terminalRepository.findByUuid(terminalUuid)
                    .orElseThrow(() -> new CartNotFoundException("Terminal not found: " + terminalUuid));
            if (terminal.getIsActive() == null || !terminal.getIsActive()) {
                throw new CartNotFoundException("Terminal is not active: " + terminalUuid);
            }

            // Validate cashier if provided
            Long cashierId = null;
            if (cashierUuid != null && !cashierUuid.trim().isEmpty()) {
                PosCashierEntity cashier = cashierRepository.findByUuid(cashierUuid)
                        .orElseThrow(() -> new CartNotFoundException("Cashier not found: " + cashierUuid));
                if (cashier.getIsActive() == null || !cashier.getIsActive()) {
                    throw new CartNotFoundException("Cashier is not active: " + cashierUuid);
                }
                cashierId = cashier.getId();
            }

            // Resolve customer: customerUuid from path param, customerType from request body
            long[] customerResolution = resolveCustomer(customerUuid, request.getCustomerType());
            Long customerId       = customerResolution[0] < 0 ? null : customerResolution[0];
            String resolvedType   = customerResolution[1] == 0 ? "WALK_IN"
                                  : customerResolution[1] == 1 ? "POS" : "ONLINE";

            log.info("New cart - customerUuid={}, customerId={}, customerType={}", customerUuid, customerId, resolvedType);

            // Create new cart
            PosCartEntity newCart = PosCartEntity.builder()
                    .terminalId(terminal.getId())
                    .cashierId(cashierId)
                    .customerId(customerId)
                    .customerType(resolvedType)
                    .isActive(true)
                    .subtotal(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .taxPercentage(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    .build();
            cart = cartRepository.save(newCart);
            log.info("Created new cart: uuid={}, terminalId={}, customerId={}", cart.getUuid(), terminal.getId(), customerId);

            finalCartUuid = cart.getUuid();
        } else {
            finalCartUuid = cartUuid;

            // Cart UUID provided, validate it exists
            PosCartEntity existingCart = cartRepository.findByUuidAndIsActiveTrue(finalCartUuid)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found or inactive: " + finalCartUuid));
            if (existingCart.getIsActive() == null || !existingCart.getIsActive()) {
                throw new CartNotFoundException("Cart not found or inactive: " + finalCartUuid);
            }

            // Convert terminalUuid to terminal ID and validate
            PosTerminalEntity terminal = terminalRepository.findByUuid(terminalUuid)
                    .orElseThrow(() -> new CartNotFoundException("Terminal not found: " + terminalUuid));

            // Verify cart belongs to this terminal
            if (!existingCart.getTerminalId().equals(terminal.getId())) {
                throw new IllegalArgumentException("Cart does not belong to terminal: " + terminalUuid);
            }

            // Resolve customer: customerUuid from path param, customerType from request body
            long[] customerResolution = resolveCustomer(customerUuid, request.getCustomerType());
            Long customerId     = customerResolution[0] < 0 ? null : customerResolution[0];
            String resolvedType = customerResolution[1] == 0 ? "WALK_IN"
                                : customerResolution[1] == 1 ? "POS" : "ONLINE";

            existingCart.setCustomerId(customerId);
            existingCart.setCustomerType(resolvedType);
            cartRepository.save(existingCart);
            log.info("Updated cart {} - customerUuid={}, customerId={}, customerType={}", finalCartUuid, customerUuid, customerId, resolvedType);

            cart = existingCart;
        }

        // Convert variantUuid to variant ID (if provided)
        Long variantId = null;
        if (request.getVariantUuid() != null && !request.getVariantUuid().isEmpty()) {

            variantId = variantLookupService.getVariantIdByUuid(request.getVariantUuid());
            if (variantId == null) {
                throw new IllegalArgumentException("Variant not found: " + request.getVariantUuid());
            }
        }

        PosProductCacheEntity product = productCacheRepository.findByUuid(request.getProductUuid())
                .orElseGet(() -> fetchAndCacheProductOnDemand(request.getProductUuid(), request.getStockAvailable()));

    ProductLookupResponse productLookup = null;
    try {
        String variantUuid = request.getVariantUuid() == null ? null : request.getVariantUuid().trim();
        if (variantUuid != null && !variantUuid.isEmpty()) {
            productLookup = crossModuleLookupService.lookupProductVariantByUuid(request.getProductUuid(), variantUuid);
            if (productLookup == null || productLookup.getFound() == null || !productLookup.getFound()) {
                log.warn("Variant lookup returned no usable response for productUuid={}, variantUuid={}, falling back to product lookup",
                        request.getProductUuid(), variantUuid);
                productLookup = crossModuleLookupService.lookupProductByUuid(request.getProductUuid());
            }
        } else {
            productLookup = crossModuleLookupService.lookupProductByUuid(request.getProductUuid());
        }
    } catch (Exception e) {
        log.warn("Cross-module product lookup failed for productUuid={}, variantUuid={}: {}",
                request.getProductUuid(), request.getVariantUuid(), e.getMessage());
        try {
            productLookup = crossModuleLookupService.lookupProductByUuid(request.getProductUuid());
        } catch (Exception fallbackEx) {
            log.warn("Fallback product lookup also failed for productUuid={}: {}",
                    request.getProductUuid(), fallbackEx.getMessage());
        }
    }

        if (product == null && productLookup == null) {
            throw new ProductNotFoundException("Product not found in cache and could not be resolved: " + request.getProductUuid());
        }

        BigDecimal unitPrice = resolveUnitPrice(productLookup, product, variantId);
        if (unitPrice == null) {
            PosProductCacheEntity refreshed = fetchAndCacheProductOnDemand(request.getProductUuid(), request.getStockAvailable());
            if (refreshed != null) {
                product = refreshed;
                unitPrice = resolveUnitPrice(productLookup, product, variantId);
            }
        }

        if (unitPrice == null) {
            throw new ProductNotFoundException("Unable to resolve unit price for product: " + request.getProductUuid());
        }

        log.info("Unit price{}",unitPrice);


        Integer availableStock = resolveAvailableStock(productLookup, product, request.getStockAvailable());
        Long resolvedProductId = resolveProductId(productLookup, product);
        if (resolvedProductId == null) {
            throw new ProductNotFoundException("Unable to resolve product ID for: " + request.getProductUuid());
        }
        if (product == null && productLookup != null && productLookup.getProductUuid() != null) {
            product = PosProductCacheEntity.builder()
                    .productId(productLookup.getProductId())
                    .uuid(productLookup.getProductUuid())
                    .sku(productLookup.getProductSku())
                    .title(productLookup.getProductTitle())
                    .showcasePrice(productLookup.getShowcasePrice())
                    .salePrice(productLookup.getSalePrice())
                    .stockAvailable(productLookup.getStockAvailable())
                    .thumbnailUrl(productLookup.getThumbnailUrl())
                    .hasVariants(productLookup.getVariantId() != null)
                    .isActive(true)
                    .build();
        }

        // Validate stock
        boolean ok = stockValidationService.validateStock(resolvedProductId, variantId, request.getQuantity());
        if (!ok) {
            throw new InsufficientStockException("Insufficient stock for product: " + request.getProductUuid());
        }

        // Lock cart for update
        PosCartEntity lockedCart = cartRepository.findByUuidForUpdate(finalCartUuid)
                .orElseThrow(() -> new CartNotFoundException("Cart not found during update: " + finalCartUuid));

        // Find existing item with same product and variant
        Optional<PosCartItemEntity> existingItemOpt;
        if (variantId != null) {
            existingItemOpt = itemRepository.findByCartIdAndProductIdAndVariantIdAndIsActiveTrue(lockedCart.getId(), resolvedProductId, variantId);
        } else {
            existingItemOpt = itemRepository.findByCartIdAndProductIdAndVariantIdIsNullAndIsActive(lockedCart.getId(), resolvedProductId,true);
        }

        PosCartItemEntity item;
        if (existingItemOpt.isPresent()) {
                item = existingItemOpt.get();
                int newQty = item.getQuantity() + request.getQuantity();
                item.setQuantity(newQty);
                item.setUnitPrice(unitPrice);
                item.setStockAvailable(availableStock);
            item.setTotalPrice(unitPrice.multiply(new BigDecimal(newQty)));
            itemRepository.save(item);
        } else {
            item = PosCartItemEntity.builder()
                    .cartId(lockedCart.getId())
                    .productId(resolvedProductId)
                    .variantId(variantId)
                    .quantity(request.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(unitPrice.multiply(new BigDecimal(request.getQuantity())))
                    .stockAvailable(availableStock)
                    .build();
            itemRepository.save(item);
        }

        // Recalculate cart totals
        List<PosCartItemEntity> items = itemRepository.findByCartIdAndIsActiveTrue(lockedCart.getId());
        BigDecimal subtotal = items.stream().map(PosCartItemEntity::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        lockedCart.setSubtotal(subtotal);
        lockedCart.calculateTaxAmount();
        lockedCart.calculateTotal();
        cartRepository.save(lockedCart);

        sendCartAddedSms(lockedCart);

        PosCartResponse resp = mapToResponse(lockedCart);

        // Update cache
        try {
            cartCacheService.addOrUpdateItem(terminalUuid, resp.getItems().get(resp.getItems().size() - 1));
            cartCacheService.cacheFullCart(terminalUuid, resp);
            cartCacheService.updateMetaAfterWrite(terminalUuid);
        } catch (Exception e) {
            log.warn("Redis write failed for cart {}: {}", terminalUuid, e.getMessage());
        }
        return resp;
    }

    @Override
    public PosCartResponse addItemToCart(String cartUuid, String productUuid, Long variantId, Integer quantity, BigDecimal unitPrice, Integer stockAvailable) {
        // Get cart to extract terminal UUID
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartUuid));

        String terminalUuid = terminalRepository.findById(cart.getTerminalId())
                .map(PosTerminalEntity::getUuid)
                .orElseThrow(() -> new CartNotFoundException("Terminal not found for cart: " + cartUuid));

        // Convert variantId (Long) to variantUuid (String) if provided
        String variantUuid = null;
        if (variantId != null) {
            // Look up variant UUID via RabbitMQ
            variantUuid = variantLookupService.getVariantUuidById(variantId);
            if (variantUuid == null) {
                throw new IllegalArgumentException("Variant not found: " + variantId);
            }
        }

        AddToCartRequest req = AddToCartRequest.builder()
                .productUuid(productUuid)
                .variantUuid(variantUuid)
                .quantity(quantity)
                .unitPrice(unitPrice == null ? null : unitPrice.doubleValue())
                .stockAvailable(stockAvailable)
                .build();
        return addItemToCart(cartUuid, terminalUuid, null, null, req);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse updateCartItemQuantity(String cartUuid, String itemUuid, Integer quantity) {
        log.info("Updating cart item quantity - cart: {}, item: {}, new quantity: {}", cartUuid, itemUuid, quantity);

        // Validate cart exists and is active
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("Cart not found:" + cartUuid));

        if (cart.getIsActive() == null || !cart.getIsActive()) {
            throw new CartNotFoundException("Cart is not active: " + cartUuid);
        }

        // Get terminal UUID for cache operations
        String terminalUuid = terminalRepository.findById(cart.getTerminalId())
                .map(PosTerminalEntity::getUuid)
                .orElse(null);

        // Validate item exists and belongs to this cart
        PosCartItemEntity item = itemRepository.findByUuid(itemUuid)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemUuid));

        if (!item.getCartId().equals(cart.getId())) {
            throw new IllegalArgumentException("Item " + itemUuid + " does not belong to cart " + cartUuid);
        }

        // Handle quantity = 0: remove item entirely
        if (quantity == 0) {
            log.info("Quantity is 0, removing item {} from cart {}", itemUuid, cartUuid);
            itemRepository.delete(item);
        } else {
            // Validate against stock snapshot
            Integer stockSnapshot = item.getStockAvailable();
            if (stockSnapshot != null && quantity > stockSnapshot) {
                log.warn("Requested quantity {} exceeds stock snapshot {} for item {} (product {}). " +
                        "Allowing update for POS UX but stock may have changed.",
                        quantity, stockSnapshot, itemUuid, item.getProductId());
                // Note: Not blocking - log warning for visibility but allow update
            }

            // Check current stock from product cache and warn if significantly changed
            try {
                PosProductCacheEntity currentProduct = productCacheRepository.findByProductId(item.getProductId())
                        .orElse(null);
                if (currentProduct != null) {
                    Integer currentStock = currentProduct.getStockAvailable();
                    if (currentStock != null && stockSnapshot != null) {
                        int stockDelta = Math.abs(currentStock - stockSnapshot);
                        if (stockDelta > 5 || (stockSnapshot > 0 && currentStock == 0)) {
                            log.warn("Stock change detected for product {}: snapshot={}, current={}. " +
                                    "Item was added with different stock availability.",
                                    item.getProductId(), stockSnapshot, currentStock);
                        }
                    }

                    // Validate against current stock for safety (soft check - log only)
                    if (currentStock != null && quantity > currentStock) {
                        log.warn("Requested quantity {} exceeds current stock {} for product {}. " +
                                "Stock may have been depleted since cart was created.",
                                quantity, currentStock, item.getProductId());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check current stock for product {}: {}", item.getProductId(), e.getMessage());
            }

            // Update quantity and recalculate line total
            item.setQuantity(quantity);
            item.setTotalPrice(item.getUnitPrice().multiply(new BigDecimal(quantity)));

            try {
                itemRepository.save(item);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.error("Optimistic locking failure updating item {}: concurrent modification detected", itemUuid);
                throw new IllegalStateException("Item was modified by another transaction. Please retry.", e);
            }
        }

        // Recalculate cart totals
        List<PosCartItemEntity> remainingItems = itemRepository.findByCartId(cart.getId());
        BigDecimal subtotal = remainingItems.stream()
                .map(PosCartItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);
        cart.calculateTaxAmount();
        cart.calculateTotal();

        try {
            cartRepository.save(cart);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure updating cart {}: concurrent modification detected", cartUuid);
            throw new IllegalStateException("Cart was modified by another transaction. Please retry.", e);
        }

        // Update Redis cache
        if (terminalUuid != null) {
            try {
                PosCartResponse resp = mapToResponse(cart);

                if (quantity == 0) {
                    // Item was removed
                    cartCacheService.removeItem(terminalUuid, itemUuid);
                }

                cartCacheService.cacheFullCart(terminalUuid, resp);
                cartCacheService.updateMetaAfterWrite(terminalUuid);

                log.debug("Updated cache for terminal {} after item quantity change", terminalUuid);
            } catch (Exception e) {
                log.warn("Failed to update cart cache after quantity change for terminal {}: {}",
                        terminalUuid, e.getMessage());
                // Cache failure doesn't block the operation - DB is source of truth
            }
        }

        log.info("Successfully updated cart item quantity - cart: {}, item: {}, quantity: {}, new total: {}",
                cartUuid, itemUuid, quantity, cart.getTotal());

        return mapToResponse(cart);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse removeCartItem(String cartUuid, String itemUuid) {
        log.info("Removing cart item - cart: {}, item: {}", cartUuid, itemUuid);

        // Validate cart exists and is active
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartUuid));

        if (cart.getIsActive() == null || !cart.getIsActive()) {
            throw new CartNotFoundException("Cart is not active: " + cartUuid);
        }

        // Validate item exists and belongs to this cart
        PosCartItemEntity item = itemRepository.findByUuid(itemUuid)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemUuid));

        if (!item.getCartId().equals(cart.getId())) {
            throw new IllegalArgumentException("Item " + itemUuid + " does not belong to cart " + cartUuid);
        }

        // Soft delete: set is_active = false
        item.setIsActive(false);
        itemRepository.save(item);
        log.info("Set is_active=false for item {} in cart {}", itemUuid, cartUuid);

        // Recalculate cart totals using only active items
        List<PosCartItemEntity> activeItems = itemRepository.findByCartIdAndIsActiveTrue(cart.getId());
        BigDecimal subtotal = activeItems.stream()
                .map(PosCartItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);
        cart.calculateTaxAmount();
        cart.calculateTotal();
        cartRepository.save(cart);

        // Update Redis cache
        String terminalUuid = terminalRepository.findById(cart.getTerminalId())
                .map(PosTerminalEntity::getUuid)
                .orElse(null);

        if (terminalUuid != null) {
            try {
                PosCartResponse resp = mapToResponse(cart);
                cartCacheService.removeItem(terminalUuid, itemUuid);
                cartCacheService.cacheFullCart(terminalUuid, resp);
                cartCacheService.updateMetaAfterWrite(terminalUuid);
                log.debug("Updated cache for terminal {} after removing item {}", terminalUuid, itemUuid);
            } catch (Exception e) {
                log.warn("Failed to update cache after removing item {} for terminal {}: {}",
                        itemUuid, terminalUuid, e.getMessage());
            }
        }

        log.info("Successfully removed cart item {} from cart {}, new total: {}", itemUuid, cartUuid, cart.getTotal());

        return mapToResponse(cart);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse applyTax(String cartUuid, BigDecimal taxPercentage) {
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartUuid));

        cart.setTaxPercentage(taxPercentage);
        cart.calculateTaxAmount();
        cart.calculateTotal();
        cartRepository.save(cart);

        String terminalUuid = terminalRepository.findById(cart.getTerminalId()).map(PosTerminalEntity::getUuid).orElse(null);
        if (terminalUuid != null) {
            try {
                cartCacheService.cacheFullCart(terminalUuid, mapToResponse(cart));
                cartCacheService.updateMetaAfterWrite(terminalUuid);
            } catch (Exception e) {
                log.warn("Failed to update cache after applying tax for {}: {}", terminalUuid, e.getMessage());
            }
        }
        return mapToResponse(cart);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse applyDiscount(String cartUuid, BigDecimal discountAmount) {
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartUuid));

        cart.setDiscountAmount(discountAmount);
        cart.calculateTotal();
        cartRepository.save(cart);

        String terminalUuid = terminalRepository.findById(cart.getTerminalId()).map(PosTerminalEntity::getUuid).orElse(null);
        if (terminalUuid != null) {
            try {
                cartCacheService.cacheFullCart(terminalUuid, mapToResponse(cart));
                cartCacheService.updateMetaAfterWrite(terminalUuid);
            } catch (Exception e) {
                log.warn("Failed to update cache after applying discount for {}: {}", terminalUuid, e.getMessage());
            }
        }
        return mapToResponse(cart);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse clearCart(String cartUuid) {
        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartUuid));

        itemRepository.deleteByCartId(cart.getId());
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTaxAmount(BigDecimal.ZERO);
        cart.setTaxPercentage(BigDecimal.ZERO);
        cart.calculateTotal();
        cartRepository.save(cart);

        String terminalUuid = terminalRepository.findById(cart.getTerminalId()).map(PosTerminalEntity::getUuid).orElse(null);
        if (terminalUuid != null) {
            try {
                cartCacheService.removeCacheForTerminal(terminalUuid);
            } catch (Exception e) {
                log.warn("Failed to remove cache after clear for {}: {}", terminalUuid, e.getMessage());
            }
        }
        return mapToResponse(cart);
    }

    @Override
    @Transactional("posTransactionManager")
    public PosCartResponse cancelCart(String cartUuid, String terminalUuid) {
        log.info("Cancelling cart - cartUuid: {}, terminalUuid: {}", cartUuid, terminalUuid);

        PosTerminalEntity terminal = terminalRepository.findByUuid(terminalUuid)
                .orElseThrow(() -> new CartNotFoundException("Terminal not found: " + terminalUuid));
        if (terminal.getIsActive() == null || !terminal.getIsActive()) {
            throw new CartNotFoundException("Terminal is not active: " + terminalUuid);
        }

        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartUuid));

        if (!cart.getTerminalId().equals(terminal.getId())) {
            throw new IllegalArgumentException("Cart does not belong to terminal: " + terminalUuid);
        }

        if (cart.getIsActive() == null || !cart.getIsActive()) {
            throw new CartNotFoundException("Cart is already cancelled: " + cartUuid);
        }

        cart.setIsActive(false);
        cartRepository.save(cart);
        log.info("Cart {} marked as inactive (cancelled)", cartUuid);

        try {
            cartCacheService.removeCacheForTerminal(terminalUuid);
            log.debug("Removed cache for terminal {} after cart cancellation", terminalUuid);
        } catch (Exception e) {
            log.warn("Failed to remove cache after cancelling cart {} for terminal {}: {}",
                    cartUuid, terminalUuid, e.getMessage());
        }

        log.info("Successfully cancelled cart {}, terminal {}", cartUuid, terminalUuid);
        return mapToResponse(cart);
    }

    /**
     * Sends an order-placed SMS to the cart's customer, resolved by the cart's customerType:
     * WALK_IN is skipped (no phone on file), POS looks up pos_customers, ONLINE resolves the
     * phone from the customers module via RabbitMQ (numeric id -> uuid -> customer lookup).
     */
    private void sendCartAddedSms(PosCartEntity cart) {
        String customerType = cart.getCustomerType();
        if (cart.getCustomerId() == null || "WALK_IN".equalsIgnoreCase(customerType)) {
            return;
        }

        String phone = resolveCustomerPhoneForSms(cart);
        if (phone == null || phone.isBlank()) {
            log.warn("Skipping cart SMS - no phone resolved for cart {} (customerType={})", cart.getUuid(), customerType);
            return;
        }

        smsNotificationService.sendOrderConfirmation(phone, cart.getUuid(), cart.getTotal(), "PENDING");
    }

    private String resolveCustomerPhoneForSms(PosCartEntity cart) {
        if ("POS".equalsIgnoreCase(cart.getCustomerType())) {
            return posCustomerRepository.findById(cart.getCustomerId())
                    .map(PosCustomerEntity::getPhone)
                    .orElse(null);
        }

        if ("ONLINE".equalsIgnoreCase(cart.getCustomerType())) {
            String customerUuid = customerLookupService.getCustomerUuidById(cart.getCustomerId());
            if (customerUuid == null || customerUuid.isBlank()) {
                return null;
            }
            CustomerLookupResponse lookup = crossModuleLookupService.lookupCustomerByUuid(customerUuid);
            return lookup != null && Boolean.TRUE.equals(lookup.getFound()) ? lookup.getPhoneNumber() : null;
        }

        return null;
    }

    private long[] resolveCustomer(String customerUuid, String customerType) {
        if (customerUuid == null || customerUuid.trim().isEmpty()) {
            log.debug("No customerUuid provided - WALK_IN");
            return new long[]{-1, 0};
        }

        if ("POS".equalsIgnoreCase(customerType)) {
            // Look up in pos_customers table by uuid
            Optional<PosCustomerEntity> posCustomer = posCustomerRepository.findByUuid(customerUuid);
            if (posCustomer.isPresent()) {
                Long id = posCustomer.get().getId();
                log.debug("POS customer found: uuid={}, id={}", customerUuid, id);
                return new long[]{id != null ? id : -1, 1};
            }
            log.warn("POS customer not found for uuid={}, falling back to WALK_IN", customerUuid);
            return new long[]{-1, 0};
        }

        // Default: ONLINE — look up via RabbitMQ in customer database
        Long id = customerLookupService.getCustomerIdByUuid(customerUuid);
        if (id != null) {
            log.debug("ONLINE customer found: uuid={}, id={}", customerUuid, id);
            return new long[]{id, 2};
        }
        log.warn("ONLINE customer not found for uuid={}, falling back to WALK_IN", customerUuid);
        return new long[]{-1, 0};
    }

    private PosDraftResponse mapToDraftResponse(PosCartEntity cart) {
        String status = "DRAFT";
        String customerType = "WALK_IN";
        String customerName = null;

        if (cart.getCustomerId() != null) {
            String cartCustomerType = cart.getCustomerType();

            if ("POS".equalsIgnoreCase(cartCustomerType)) {
                // POS: look up in pos_customers table by numeric id → get full_name
                customerType = "POS";
                try {
                    PosCustomerEntity posCustomer = posCustomerRepository.findById(cart.getCustomerId()).orElse(null);
                    if (posCustomer != null) {
                        customerName = posCustomer.getFullName();
                        log.debug("POS customer name resolved: id={}, name={}", cart.getCustomerId(), customerName);
                    } else {
                        log.warn("POS customer not found for id={}, customerName will be null", cart.getCustomerId());
                    }
                } catch (Exception e) {
                    log.warn("Error fetching POS customer for id={}: {}", cart.getCustomerId(), e.getMessage());
                }

            } else if ("ONLINE".equalsIgnoreCase(cartCustomerType)) {
                // ONLINE: look up in customers table in beyos_customers_db by numeric id → use Customer.getFullName()
                customerType = "ONLINE";
                try {
                    // Step 1: convert numeric customerId -> customerUuid via PosCustomerLookupService (RPC)
                    String custUuid = customerLookupService.getCustomerUuidById(cart.getCustomerId());
                    if (custUuid != null && !custUuid.trim().isEmpty()) {
                        // Step 2: lookup customer by UUID using CrossModuleLookupService (shared DTO contains firstName/lastName)
                        CustomerLookupResponse lookup = crossModuleLookupService.lookupCustomerByUuid(custUuid);
                        if (lookup != null && Boolean.TRUE.equals(lookup.getFound())) {
                            String first = lookup.getFirstName();
                            String last = lookup.getLastName();
                            StringBuilder sb = new StringBuilder();
                            if (first != null && !first.trim().isEmpty()) sb.append(first.trim());
                            if (last != null && !last.trim().isEmpty()) {
                                if (!sb.isEmpty()) sb.append(' ');
                                sb.append(last.trim());
                            }
                            customerName = sb.length() > 0 ? sb.toString() : null;
                            log.debug("ONLINE customer name resolved via cross-module lookup: id={}, uuid={}, name={}", cart.getCustomerId(), custUuid, customerName);
                        } else {
                            log.warn("ONLINE customer cross-module lookup not found for uuid={} (id={}), customerName will be null", custUuid, cart.getCustomerId());
                        }
                    } else {
                        log.warn("Could not resolve customer UUID for id={} before cross-module lookup", cart.getCustomerId());
                    }
                } catch (Exception e) {
                    log.warn("Error fetching ONLINE customer via cross-module lookup for id={}: {}", cart.getCustomerId(), e.getMessage());
                }

            } else {
                // customerType on cart is missing or unknown — keep WALK_IN
                log.warn("Cart {} has customerId={} but unknown customerType='{}', treating as WALK_IN",
                        cart.getUuid(), cart.getCustomerId(), cartCustomerType);
                customerType = cartCustomerType != null ? cartCustomerType : "WALK_IN";
            }
        }

        return PosDraftResponse.builder()
                .cartUuid(cart.getUuid())
                .customerName(customerName)
                .customerType(customerType)
                .createdAt(cart.getCreatedAt())
                .subTotal(cart.getSubtotal() == null ? 0.0 : cart.getSubtotal().doubleValue())
                .status(status)
                .build();
    }

    private PosCartResponse mapToResponse(PosCartEntity cart) {
        List<PosCartItemEntity> items = itemRepository.findByCartIdAndIsActiveTrue(cart.getId());
        List<PosCartItemResponse> itemResponses = items.stream().map(item -> {
                Optional<PosProductCacheEntity> productCache = productCacheRepository.findByProductId(item.getProductId());
                String productName = productCache.map(PosProductCacheEntity::getTitle).orElse(null);
                String productSku = productCache.map(PosProductCacheEntity::getSku).orElse(null);

                return PosCartItemResponse.builder()
                        .uuid(item.getUuid())
                        .productId(item.getProductId())
                        .productName(productName)
                        .productSku(productSku)
                        .variantId(item.getVariantId())
                        .attributeSummary("")
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice() == null ? null : item.getUnitPrice().doubleValue())
                        .totalPrice(item.getTotalPrice() == null ? null : item.getTotalPrice().doubleValue())
                        .stockAvailable(item.getStockAvailable())
                        .createdAt(item.getCreatedAt())
                        .updatedAt(item.getUpdatedAt())
                        .build();
        }).toList();

        // Populate draft summaries for the same terminal if terminalId is available
        List<PosDraftResponse> draftResponses = List.of();
        if (cart.getTerminalId() != null) {
            try {
                List<PosCartEntity> draftCarts = cartRepository.findDraftCartsByTerminalIdOrderByUpdatedAtDesc(cart.getTerminalId());
                if (draftCarts != null && !draftCarts.isEmpty()) {
                    draftResponses = draftCarts.stream()
                            .map(this::mapToDraftResponse)
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Failed to load draft carts for terminal {}: {}", cart.getTerminalId(), e.getMessage());
                draftResponses = List.of();
            }
        }

        return PosCartResponse.builder()
                .uuid(cart.getUuid())
                .items(itemResponses)
                .posDraftResponses(draftResponses)
                .subtotal(cart.getSubtotal() == null ? 0.0 : cart.getSubtotal().doubleValue())
                .taxAmount(cart.getTaxAmount() == null ? 0.0 : cart.getTaxAmount().doubleValue())
                .taxPercentage(cart.getTaxPercentage() == null ? 0.0 : cart.getTaxPercentage().doubleValue())
                .discountAmount(cart.getDiscountAmount() == null ? 0.0 : cart.getDiscountAmount().doubleValue())
                .total(cart.getTotal() == null ? 0.0 : cart.getTotal().doubleValue())
                .cashierId(cart.getCashierId())
                .terminalId(cart.getTerminalId())
                .customerId(cart.getCustomerId())
                .isActive(cart.getIsActive())
                .isDraft(cart.getIsDraft())
                .createdAt(cart.getUpdatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    public void warmupCacheForCart(String terminalUuid, PosCartEntity cart) {
        try {
            PosCartResponse resp = mapToResponse(cart);
            cartCacheService.cacheFullCart(terminalUuid, resp);
            log.debug("Warmed up cache for terminal {} with cart {}", terminalUuid, cart.getUuid());
        } catch (Exception e) {
            log.warn("Failed to warm up cache for terminal {}: {}", terminalUuid, e.getMessage());
        }
    }

    /**
     * On-demand product sync fallback.
     * Called when a product is not found in pos_product_cache.
     * Queries the product module via RabbitMQ and inserts the product into cache.
     */
    @Transactional("posTransactionManager")
    public PosProductCacheEntity fetchAndCacheProductOnDemand(String productUuid,Integer stockAvailable) {
        log.warn("[POS_CACHE] Product UUID {} not found in cache - attempting on-demand sync via RabbitMQ", productUuid);

        try {
            ProductLookupRequest lookupRequest = ProductLookupRequest.builder()
                    .productUuid(productUuid)
                    .requestId(UUID.randomUUID().toString())
                    .build();

            // Do NOT call rabbitTemplate.setReplyTimeout() — it mutates the shared singleton
            Object rawResponse = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "product.lookup.request",
                    lookupRequest
            );
            ProductLookupResponse response = convertLookupResponse(rawResponse);
            log.info("response: {}", response);

            if (response == null || !Boolean.TRUE.equals(response.getFound())) {
                log.warn("[POS_CACHE] Product not found in product module for UUID: {}", productUuid);
                return null;
            }

            // Build and save cache entry from the RabbitMQ response
            LocalDateTime now = LocalDateTime.now();

// Check if product already exists in cache
            Optional<PosProductCacheEntity> existingOpt = productCacheRepository.findByProductId(response.getProductId());

            PosProductCacheEntity entity;
            if (existingOpt.isPresent()) {
                log.info("[POS_CACHE] Product already exists in cache for productId {} - updating existing entry", response.getSalePrice());
                // Update existing entity
                entity = existingOpt.get();
                entity.setSku(response.getProductSku());
                entity.setTitle(response.getProductTitle());
                entity.setShowcasePrice(response.getShowcasePrice() != null ? response.getShowcasePrice() : BigDecimal.ZERO);
                entity.setSalePrice(response.getSalePrice());
                entity.setStockAvailable(response.getStockAvailable() != null ? response.getStockAvailable() : stockAvailable);
                entity.setThumbnailUrl(response.getThumbnailUrl());
                entity.setIsActive(true);
                entity.setSyncedAt(now);
                entity.setDateUpdated(now);
            } else {
                log.info("[POS_CACHE] Product does not exist in cache for productId {} - creating new cache entry", response.getProductId());
                // Create new entity
                entity = PosProductCacheEntity.builder()
                        .productId(response.getProductId())
                        .uuid(response.getProductUuid())
                        .sku(response.getProductSku())
                        .title(response.getProductTitle())
                        .showcasePrice(response.getShowcasePrice() != null ? response.getShowcasePrice() : BigDecimal.ZERO)
                        .salePrice(response.getSalePrice())
                        .stockAvailable(response.getStockAvailable() != null ? response.getStockAvailable() : stockAvailable)
                        .thumbnailUrl(response.getThumbnailUrl())
                        .isActive(true)
                        .hasVariants(false)
                        .syncedAt(now)
                        .dateCreated(now)
                        .dateUpdated(now)
                        .build();
            }

            entity = productCacheRepository.save(entity);
            log.info("ENity: {}", entity.getSalePrice());
            log.info("[POS_CACHE] On-demand sync successful - productId={}, uuid={}, title={}",
                    entity.getProductId(), entity.getUuid(), entity.getTitle());

            return entity;

        } catch (Exception e) {
            log.error("[POS_CACHE] On-demand sync failed for UUID {}: {}", productUuid, e.getMessage());
            return null;
        }
    }

    private BigDecimal resolveUnitPrice(ProductLookupResponse productLookup,
                                        PosProductCacheEntity cachedProduct,
                                        Long variantId) {
        if (productLookup != null) {
            if (variantId != null) {
                BigDecimal variantPrice = firstNonNull(productLookup.getVariantSalePrice(), productLookup.getVariantShowcasePrice());
                if (variantPrice != null) {
                    return variantPrice;
                }
            }

            BigDecimal productPrice = firstNonNull(productLookup.getSalePrice(), productLookup.getShowcasePrice());
            if (productPrice != null) {
                return productPrice;
            }
        }

        if (cachedProduct != null) {
            BigDecimal cachedPrice = firstNonNull(cachedProduct.getSalePrice(), cachedProduct.getShowcasePrice());
            if (cachedPrice != null) {
                return cachedPrice;
            }
        }

        return null;
    }

    private Integer resolveAvailableStock(ProductLookupResponse productLookup,
                                         PosProductCacheEntity cachedProduct,
                                         Integer requestStockAvailable) {
        if (productLookup != null && productLookup.getStockAvailable() != null) {
            return productLookup.getStockAvailable();
        }
        if (cachedProduct != null && cachedProduct.getStockAvailable() != null) {
            return cachedProduct.getStockAvailable();
        }
        return requestStockAvailable;
    }

    private Long resolveProductId(ProductLookupResponse productLookup, PosProductCacheEntity cachedProduct) {
        if (productLookup != null && productLookup.getProductId() != null) {
            return productLookup.getProductId();
        }
        if (cachedProduct != null) {
            return cachedProduct.getProductId();
        }
        return null;
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private ProductLookupResponse convertLookupResponse(Object rawResponse) {
        if (rawResponse == null) {
            return null;
        }

        if (rawResponse instanceof ProductLookupResponse response) {
            return response;
        }

        if (rawResponse instanceof String json) {
            try {
                return objectMapper.readValue(json, ProductLookupResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to convert RabbitMQ response to ProductLookupResponse", e);
            }
        }

        return objectMapper.convertValue(rawResponse, ProductLookupResponse.class);
    }

    @Override
    @Transactional("posTransactionManager")
    public List<PosCartResponse> markCartAsDraft(List<String> cartUuids, String terminalUuid) {
        log.info("Marking {} carts as draft for terminal {}", cartUuids.size(), terminalUuid);

        PosTerminalEntity terminal = terminalRepository.findByUuid(terminalUuid)
                .orElseThrow(() -> new CartNotFoundException("Terminal not found: " + terminalUuid));

        if (terminal.getIsActive() == null || !terminal.getIsActive()) {
            throw new CartNotFoundException("Terminal is not active: " + terminalUuid);
        }

        List<PosCartResponse> responses = new ArrayList<>();

        for (String cartUuid : cartUuids) {
            try {
                PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                        .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartUuid));

                if (!cart.getTerminalId().equals(terminal.getId())) {
                    log.warn("Cart {} does not belong to terminal {}, skipping", cartUuid, terminalUuid);
                    continue;
                }

                cart.setIsDraft(true);
                cart = cartRepository.save(cart);

                PosCartResponse resp = mapToResponse(cart);
                responses.add(resp);

                try {
                    cartCacheService.cacheFullCart(terminalUuid, resp);
                    cartCacheService.updateMetaAfterWrite(terminalUuid);
                } catch (Exception e) {
                    log.warn("Failed to update cache after marking cart {} as draft for terminal {}: {}", cartUuid, terminalUuid, e.getMessage());
                }

                log.info("Cart {} marked as draft successfully", cartUuid);
            } catch (Exception e) {
                log.error("Failed to mark cart {} as draft: {}", cartUuid, e.getMessage(), e);
            }
        }

        log.info("Successfully marked {} carts as draft out of {} requested", responses.size(), cartUuids.size());
        return responses;
    }

}

package org.psint.beyosclothing.modules.resellers.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupRequest;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupResponse;
import org.psint.beyosclothing.modules.resellers.dto.external.StockCheckResponse;
import org.psint.beyosclothing.modules.resellers.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateCartItemRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerCartResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCart;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCartItem;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartItemRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerCartService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Cart Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerCartServiceImpl implements ResellerCartService {

    private final ResellerCartRepository cartRepository;
    private final ResellerCartItemRepository cartItemRepository;
    private final ResellerService resellerService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.product:beyos.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    @Override
    public String getOrCreateCart(Long resellerId) {
        ResellerCart cart = cartRepository.findByResellerIdAndIsActive(resellerId, true)
                .orElseGet(() -> {
                    ResellerCart newCart = ResellerCart.builder()
                            .resellerId(resellerId)
                            .subtotal(BigDecimal.ZERO)
                            .total(BigDecimal.ZERO)
                            .isActive(true)
                            .build();
                    return cartRepository.save(newCart);
                });
        return cart.getUuid();
    }

    @Override
    public ResellerCartResponse addToCart(Long resellerId, AddToCartRequest request) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);

        // Get or create cart
        ResellerCart cart = cartRepository.findByResellerIdAndIsActive(reseller.getId(), true)
                .orElseGet(() -> {
                    ResellerCart newCart = ResellerCart.builder()
                            .resellerId(reseller.getId())
                            .subtotal(BigDecimal.ZERO)
                            .total(BigDecimal.ZERO)
                            .isActive(true)
                            .build();
                    return cartRepository.save(newCart);
                });

        // Fetch product details via RabbitMQ
        ProductDetails productDetails = fetchProductDetails(request.getProductUuid(), request.getVariantUuid());

        // Validate stock
        validateStock(productDetails.getProductId(), productDetails.getVariantId(), request.getQuantity());

        // Check if item already in cart (active only)
        ResellerCartItem existingItem = findExistingCartItem(cart.getId(),
                                                             productDetails.getProductId(),
                                                             productDetails.getVariantId());

        if (existingItem != null) {
            // Update quantity
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            existingItem.setQuantity(newQuantity);

            // Resolve effective override price considering wholesale
            BigDecimal resolvedOverride = resolveWholesalePrice(
                    request.getOverridePrice(),
                    productDetails.getWholesalePrice(),
                    productDetails.getWholesaleMinQty(),
                    newQuantity
            );

            updateCartItem(existingItem, resolvedOverride, reseller);
        } else {
            // Resolve effective override price considering wholesale
            BigDecimal resolvedOverride = resolveWholesalePrice(
                    request.getOverridePrice(),
                    productDetails.getWholesalePrice(),
                    productDetails.getWholesaleMinQty(),
                    request.getQuantity()
            );

            // Create new cart item
            ResellerCartItem cartItem = ResellerCartItem.builder()
                    .cartId(cart.getId())
                    .productId(productDetails.getProductId())
                    .variantId(productDetails.getVariantId())
                    .productName(productDetails.getProductName())
                    .variantName(productDetails.getVariantName())
                    .baseUnitPrice(productDetails.getResellerPrice())
                    .overrideUnitPrice(resolvedOverride)
                    .quantity(request.getQuantity())
                    .isActive(true)
                    .build();

//            validatePriceOverride(cartItem, reseller);
            cartItem.calculateTotalPrice();
            cartItem.calculateMargin();

            cartItemRepository.save(cartItem);
        }

        // Recalculate cart totals
        recalculateCartTotals(cart);

        return getCart(resellerId);
    }

    @Override
    public ResellerCartResponse updateCartItem(Long resellerId, UpdateCartItemRequest request) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);
        ResellerCart cart = getActiveCart(reseller.getId());

        ResellerCartItem cartItem = cartItemRepository.findByCartIdAndUuidAndIsActive(cart.getId(), request.getCartItemUuid(), true)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        cartItem.setQuantity(request.getQuantity());
        updateCartItem(cartItem, request.getOverridePrice(), reseller);

        recalculateCartTotals(cart);

        return getCart(resellerId);
    }

    @Override
    public ResellerCartResponse removeCartItem(Long resellerId, String cartItemUuid) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);
        ResellerCart cart = getActiveCart(reseller.getId());

        ResellerCartItem cartItem = cartItemRepository.findByCartIdAndUuidAndIsActive(cart.getId(), cartItemUuid, true)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        // Soft delete: mark as inactive instead of deleting the record
        cartItem.setIsActive(false);
        cartItemRepository.save(cartItem);

        recalculateCartTotals(cart);

        return getCart(resellerId);
    }

    @Override
    public void clearCart(Long resellerId) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);
        ResellerCart cart = getActiveCart(reseller.getId());

        // Soft delete: mark all active items as inactive instead of deleting them
        cartItemRepository.softDeleteByCartId(cart.getId());
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cartRepository.save(cart);

        log.info("Cart cleared for reseller: {}", reseller.getUuid());
    }

    @Override
    public ResellerCartResponse getCart(Long resellerId) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);
        ResellerCart cart = getActiveCart(reseller.getId());

        List<ResellerCartItem> items = cartItemRepository.findByCartIdAndIsActive(cart.getId(), true);

        List<ResellerCartResponse.CartItemDetail> itemDetails = items.stream()
                .map(this::mapToCartItemDetail)
                .collect(Collectors.toList());

        BigDecimal totalMargin = items.stream()
                .map(ResellerCartItem::getMarginAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResellerCartResponse.builder()
                .cartUuid(cart.getUuid())
                .items(itemDetails)
                .subtotal(cart.getSubtotal())
                .taxAmount(cart.getTaxAmount())
                .discountAmount(cart.getDiscountAmount())
                .total(cart.getTotal())
                .totalMargin(totalMargin)
                .itemCount(items.size())
                .build();
    }

    @Override
    public Long getCartItemCount(Long resellerId) {
        Reseller reseller = resellerService.getResellerByUserId(resellerId);
        return cartRepository.countActiveItemsByResellerId(reseller.getId());
    }

    // Helper methods
    private ResellerCart getActiveCart(Long resellerId) {
        return cartRepository.findByResellerIdAndIsActive(resellerId, true)
                .orElseThrow(() -> new IllegalArgumentException("No active cart found"));
    }

    private ResellerCartItem findExistingCartItem(Long cartId, Long productId, Long variantId) {
        if (variantId != null) {
            return cartItemRepository.findByCartIdAndProductIdAndVariantIdAndIsActive(cartId, productId, variantId, true)
                    .orElse(null);
        } else {
            return cartItemRepository.findByCartIdAndProductIdAndVariantIdIsNullAndIsActive(cartId, productId)
                    .orElse(null);
        }
    }

    private void updateCartItem(ResellerCartItem cartItem, BigDecimal overridePrice, Reseller reseller) {
        if (overridePrice != null) {
            cartItem.setOverrideUnitPrice(overridePrice);
//            validatePriceOverride(cartItem, reseller);
        }
        cartItem.calculateTotalPrice();
        cartItem.calculateMargin();
        cartItemRepository.save(cartItem);
    }

    private void validatePriceOverride(ResellerCartItem cartItem, Reseller reseller) {
        if (cartItem.hasOverride()) {
            if (!reseller.getAllowPriceOverride()) {
                throw new IllegalArgumentException("Price override not allowed for this reseller");
            }
            log.info("Reseller {} has price override: base price {}, override price {}",
                     reseller.getUuid(),
                     cartItem.getBaseUnitPrice(),
                     cartItem.getOverrideUnitPrice());

            BigDecimal markup = cartItem.getOverrideUnitPrice()
                    .subtract(cartItem.getBaseUnitPrice())
                    .divide(cartItem.getBaseUnitPrice(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (reseller.getMinAllowedMarkupPct() != null &&
                markup.compareTo(reseller.getMinAllowedMarkupPct()) < 0) {
                throw new IllegalArgumentException("Price override below minimum allowed markup");
            }

            if (reseller.getMaxAllowedMarkupPct() != null &&
                markup.compareTo(reseller.getMaxAllowedMarkupPct()) > 0) {
                throw new IllegalArgumentException("Price override exceeds maximum allowed markup");
            }
        }
    }

    private void recalculateCartTotals(ResellerCart cart) {
        List<ResellerCartItem> items = cartItemRepository.findByCartIdAndIsActive(cart.getId(), true);

        BigDecimal subtotal = items.stream()
                .map(ResellerCartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);
        cart.calculateTotal();
        cartRepository.save(cart);
    }

    private ProductDetails fetchProductDetails(String productUuid, String variantUuid) {
        log.info("Fetching product details via RabbitMQ for: {} variant: {}", productUuid, variantUuid);

        try {
           ProductLookupRequest lookupRequest = ProductLookupRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .productUuid(productUuid)
                    .variantUuid(variantUuid)
                    .build();

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                productExchange,
                "product.lookup.request",
                lookupRequest
            );
            log.info("Received response from product module: {}", response != null ? response.getClass().getName() : "null");

            if (response == null) {
                throw new IllegalStateException(
                        "No response from product module (timeout or routing issue). " +
                        "Verify RabbitMQ binding: productExchange -> product.lookup.request"
                );
            }

            ProductLookupResponse productResponse = parseProductLookupResponse(response);

            if (productResponse != null && Boolean.TRUE.equals(productResponse.getFound())) {
                Long productId = productResponse.getProductId();
                Long variantId = productResponse.getVariantId();
                String productName = productResponse.getProductTitle();
                String variantName = productResponse.getVariantAttributeSummary() != null
                    ? productResponse.getVariantAttributeSummary()
                    : "";

                // Use reseller price if available, otherwise fall back to sale/showcase price
                BigDecimal resellerPrice;
                if (variantUuid != null && productResponse.getVariantResellerPrice() != null) {
                    resellerPrice = productResponse.getVariantResellerPrice();
                } else if (variantUuid != null && productResponse.getVariantSalePrice() != null) {
                    resellerPrice = productResponse.getVariantSalePrice();
                } else if (variantUuid != null && productResponse.getVariantShowcasePrice() != null) {
                    resellerPrice = productResponse.getVariantShowcasePrice();
                } else if (productResponse.getSalePrice() != null) {
                    resellerPrice = productResponse.getSalePrice();
                } else {
                    resellerPrice = productResponse.getShowcasePrice();
                }

                // Carry wholesale data through — null if not configured on the product/variant
                return new ProductDetails(
                        productId, variantId, productName, variantName, resellerPrice,
                        productResponse.getWholesalePrice(),
                        productResponse.getWholesaleMinQty()
                );
            } else if (productResponse != null) {
                String error = productResponse.getErrorMessage();
                throw new IllegalArgumentException("Product not found: " + error);
            }

            log.error("Product module reply could not be parsed. responseType={}, payload={}",
                    response.getClass().getName(), summarizeResponse(response));
            throw new IllegalArgumentException("Invalid response from product module");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching product details via RabbitMQ", e);
            throw new IllegalStateException("Failed to fetch product details: " + e.getMessage(), e);
        }
    }

    private void validateStock(Long productId, Long variantId, Integer quantity) {
        log.info("Validating stock via RabbitMQ for product: {} variant: {} quantity: {}", productId, variantId, quantity);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", java.util.UUID.randomUUID().toString());
            request.put("productId", productId);
            request.put("variantId", variantId);
            request.put("requestedQuantity", quantity);
//            request.put("action", "CHECK");

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                inventoryExchange,
                "inventory.stock.check",
                request
            );

            if (response == null) {
                throw new IllegalStateException(
                        "No response from inventory module (timeout or routing issue). " +
                        "Verify RabbitMQ binding: inventoryExchange -> inventory.stock.check"
                );
            }

            StockCheckResponse stockResponse = parseStockCheckResponse(response);
            if (stockResponse == null) {
                log.error("Invalid stock validation response. responseType={}, payload={}",
                        response.getClass().getName(), summarizeResponse(response));
                throw new IllegalStateException("Invalid response from inventory module");
            }

            Boolean isAvailable = stockResponse.getIsAvailable();
            Integer availableStock = stockResponse.getAvailableStock();
            log.info("Stock validation - isAvailable: {}, availableStock: {}", isAvailable, availableStock);

            if (isAvailable == null) {
                log.error("Inventory response missing isAvailable flag. payload={}", summarizeResponse(response));
                throw new IllegalStateException("Invalid response from inventory module");
            }

            if (!Boolean.TRUE.equals(isAvailable)) {
                log.info("Insufficient stock for product: {} variant: {}. Requested: {}, Available: {}",
                        productId, variantId, quantity, availableStock);
                throw new IllegalArgumentException(
                        String.format("Insufficient stock. Requested: %d, Available: %d", quantity, availableStock != null ? availableStock : 0)
                );
            }

            // Stock is available - validation passed
            log.debug("Stock validation passed - Available: {}", availableStock);
            return;

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Error validating stock via RabbitMQ", e);
            throw new IllegalStateException("Failed to validate stock: " + e.getMessage(), e);
        }
    }

    private ResellerCartResponse.CartItemDetail mapToCartItemDetail(ResellerCartItem item) {
        return ResellerCartResponse.CartItemDetail.builder()
                .uuid(item.getUuid())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .basePrice(item.getBaseUnitPrice())
                .overridePrice(item.getOverrideUnitPrice())
                .effectivePrice(item.getEffectivePrice())
                .quantity(item.getQuantity())
                .totalPrice(item.getTotalPrice())
                .margin(item.getMarginAmount())
                .hasOverride(item.hasOverride())
                .build();
    }

    /**
     * Resolves the effective price to apply as the override unit price.
     *
     * Wholesale pricing rules:
     * - Both wholesalePrice AND wholesaleMinQty must be non-null for wholesale to apply.
     * - If quantity >= wholesaleMinQty, use wholesalePrice as the override (takes priority over any
     *   caller-supplied overridePrice since wholesale is a cost-basis reduction).
     * - If quantity < wholesaleMinQty, or wholesale is not configured, fall back to the
     *   caller-supplied overridePrice (which may itself be null — meaning no override).
     *
     * @param callerOverridePrice  The price the reseller explicitly typed in (may be null)
     * @param wholesalePrice       Wholesale unit price from the product (may be null)
     * @param wholesaleMinQty      Minimum quantity for wholesale to kick in (may be null)
     * @param quantity             The total quantity in cart for this item
     * @return resolved override price, or null if no override should be applied
     */
    private BigDecimal resolveWholesalePrice(BigDecimal callerOverridePrice,
                                             BigDecimal wholesalePrice,
                                             Integer wholesaleMinQty,
                                             int quantity) {
        // Wholesale only applies when BOTH price and minQty are configured
        if (wholesalePrice != null && wholesaleMinQty != null) {
            if (quantity >= wholesaleMinQty) {
                log.info("Wholesale pricing applied: quantity={} >= minQty={}, wholesalePrice={}",
                        quantity, wholesaleMinQty, wholesalePrice);
                return wholesalePrice;
            } else {
                log.debug("Wholesale NOT applied: quantity={} < minQty={}", quantity, wholesaleMinQty);
            }
        }
        // Fall back to whatever the reseller explicitly passed in
        return callerOverridePrice;
    }

    private ProductLookupResponse parseProductLookupResponse(Object response) {
        try {
            if (response instanceof ProductLookupResponse) {
                return (ProductLookupResponse) response;
            }
            if (response instanceof Message) {
                return objectMapper.readValue(((Message) response).getBody(), ProductLookupResponse.class);
            }
            if (response instanceof byte[]) {
                return objectMapper.readValue((byte[]) response, ProductLookupResponse.class);
            }
            if (response instanceof String) {
                return objectMapper.readValue((String) response, ProductLookupResponse.class);
            }
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                return objectMapper.convertValue(unwrapPayload(responseMap), ProductLookupResponse.class);
            }
            return objectMapper.convertValue(response, ProductLookupResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse product lookup response. responseType={}, payload={}",
                    response != null ? response.getClass().getName() : "null", summarizeResponse(response), e);
            return null;
        }
    }

    private StockCheckResponse parseStockCheckResponse(Object response) {
        try {
            if (response instanceof StockCheckResponse) {
                return (StockCheckResponse) response;
            }
            if (response instanceof Message) {
                return objectMapper.readValue(((Message) response).getBody(), StockCheckResponse.class);
            }
            if (response instanceof byte[]) {
                return objectMapper.readValue((byte[]) response, StockCheckResponse.class);
            }
            if (response instanceof String) {
                return objectMapper.readValue((String) response, StockCheckResponse.class);
            }
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Map<String, Object> payload = unwrapPayload(responseMap);
                StockCheckResponse mapped = objectMapper.convertValue(payload, StockCheckResponse.class);
                if (mapped.getIsAvailable() == null) {
                    mapped.setIsAvailable(asBoolean(payload.get("isAvailable")));
                }
                if (mapped.getAvailableStock() == null) {
                    mapped.setAvailableStock(asInteger(payload.get("availableStock")));
                }
                if (mapped.getFound() == null) {
                    mapped.setFound(asBoolean(payload.get("found")));
                }
                return mapped;
            }
            return objectMapper.convertValue(response, StockCheckResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse stock response. responseType={}, payload={}",
                    response != null ? response.getClass().getName() : "null", summarizeResponse(response), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapPayload(Map<String, Object> rawMap) {
        Object body = rawMap.get("body");
        if (body instanceof Map) {
            return (Map<String, Object>) body;
        }

        Object data = rawMap.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }

        Object payload = rawMap.get("payload");
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }

        return rawMap;
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String summarizeResponse(Object response) {
        if (response == null) {
            return "null";
        }
        if (response instanceof Message) {
            return new String(((Message) response).getBody(), StandardCharsets.UTF_8);
        }
        if (response instanceof byte[]) {
            return new String((byte[]) response, StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ignored) {
            return String.valueOf(response);
        }
    }

    // Inner class for product details
    private static class ProductDetails {
        private final Long productId;
        private final Long variantId;
        private final String productName;
        private final String variantName;
        private final BigDecimal resellerPrice;
        // Wholesale pricing — both must be non-null for wholesale to be applicable
        private final BigDecimal wholesalePrice;
        private final Integer wholesaleMinQty;

        public ProductDetails(Long productId, Long variantId, String productName,
                              String variantName, BigDecimal resellerPrice,
                              BigDecimal wholesalePrice, Integer wholesaleMinQty) {
            this.productId = productId;
            this.variantId = variantId;
            this.productName = productName;
            this.variantName = variantName;
            this.resellerPrice = resellerPrice;
            this.wholesalePrice = wholesalePrice;
            this.wholesaleMinQty = wholesaleMinQty;
        }

        public Long getProductId() { return productId; }
        public Long getVariantId() { return variantId; }
        public String getProductName() { return productName; }
        public String getVariantName() { return variantName; }
        public BigDecimal getResellerPrice() { return resellerPrice; }
        public BigDecimal getWholesalePrice() { return wholesalePrice; }
        public Integer getWholesaleMinQty() { return wholesaleMinQty; }
    }
}


package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCartItemEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.exception.*;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCashierRepository;
import org.psint.beyosclothing.modules.pos.repository.PosReceiptRepository;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.psint.beyosclothing.modules.pos.service.PosOrderCreationService;
import org.psint.beyosclothing.modules.pos.service.PosOrderPlacementService;
import org.psint.beyosclothing.modules.pos.service.PosStockValidationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * POS Order Placement Service Implementation
 * Comprehensive validation before order creation
 * Prevents race conditions and ensures data consistency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosOrderPlacementServiceImpl implements PosOrderPlacementService {

    private final PosCartRepository cartRepository;
    private final PosCartItemRepository cartItemRepository;
    private final PosTerminalRepository terminalRepository;
    private final PosCashierRepository cashierRepository;
    private final PosReceiptRepository receiptRepository;
    private final PosStockValidationService stockValidationService;
    private final PosOrderCreationService orderCreationService;
    private final RabbitTemplate rabbitTemplate;
    private final PosCartCacheService cartCacheService;

    private static final String PAYMENT_METHOD_LOOKUP_QUEUE = "payment.method.lookup.request";
    private static final String CUSTOMER_DETAILS_QUEUE = "customer.details.request.queue";
    private static final long RPC_TIMEOUT_MS = 1000L;
    private static final Pattern FOUR_DIGITS_PATTERN = Pattern.compile("^\\d{4}$");

    @Override
    @Transactional("posTransactionManager")
    public PosOrderResponse placeOrder(PosPlaceOrderRequest request) {
        log.info("Starting POS order placement - cartUuid: {}, paymentMethod: {}, customerId: {}",
                request.getCartUuid(), request.getPaymentMethodId(), request.getCustomerId());

        PosCartEntity cart = validateCart(request.getCartUuid());
        validateCartNotEmpty(cart);
        validatePaymentMethod(request.getPaymentMethodId());
        validateCardDetails(request);

        if (request.getCustomerId() != null) {
            validateCustomer(request.getCustomerId());
        } else {
            log.info("Walk-in customer - no customer validation required");
        }

        validateStockAvailability(cart);
        log.info("All validations passed - creating order for cart: {}", request.getCartUuid());

        PosOrderResponse response = createOrder(cart, request);
        log.info("Order created successfully - orderUuid: {}", response.getOrderUuid());
        return response;
    }

    /**
     * Step 1: Validate cart exists and is active
     */
    private PosCartEntity validateCart(String cartUuid) {
        log.debug("Validating cart exists and is active: {}", cartUuid);

        PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(cartUuid)
                .orElseThrow(() -> {
                    log.error("Cart not found: {}", cartUuid);
                    return new CartNotFoundException("Cart not found with UUID: " + cartUuid);
                });

        if (!Boolean.TRUE.equals(cart.getIsActive())) {
            log.error("Cart is not active: {}", cartUuid);
            throw new CartNotFoundException("Cart is not active: " + cartUuid);
        }

        log.debug("Cart validation passed: {}", cartUuid);
        return cart;
    }

    /**
     * Step 2: Validate cart contains at least one item
     */
    private void validateCartNotEmpty(PosCartEntity cart) {
        log.debug("Validating cart is not empty: {}", cart.getUuid());

        List<PosCartItemEntity> items = cartItemRepository.findByCartId(cart.getId());

        if (items == null || items.isEmpty()) {
            log.error("Cart is empty: {}", cart.getUuid());
            throw new EmptyCartException("Cannot checkout with empty cart: " + cart.getUuid());
        }

        log.debug("Cart contains {} items", items.size());
    }

    /**
     * Step 3: Validate payment method exists and is active via RabbitMQ
     */
    private void validatePaymentMethod(Long paymentMethodId) {
        log.debug("Validating payment method: {}", paymentMethodId);

        try {
            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            Map<String, Object> request = Map.of("paymentMethodId", paymentMethodId);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rabbitTemplate.convertSendAndReceive(
                    PAYMENT_METHOD_LOOKUP_QUEUE,
                    request
            );

            if (response == null) {
                log.error("Payment method validation returned null: {}", paymentMethodId);
                throw new PaymentMethodNotFoundException(
                        "Payment method not found or inactive: " + paymentMethodId);
            }

            Boolean isActive = (Boolean) response.get("isActive");
            if (!Boolean.TRUE.equals(isActive)) {
                log.error("Payment method is not active: {}", paymentMethodId);
                throw new PaymentMethodNotFoundException(
                        "Payment method is not active: " + paymentMethodId);
            }

            log.debug("Payment method validation passed: {}", paymentMethodId);

        } catch (PaymentMethodNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate payment method {}: {}", paymentMethodId, e.getMessage());
            throw new PaymentMethodNotFoundException(
                    "Failed to validate payment method: " + paymentMethodId);
        }
    }

    /**
     * Step 4: Validate card details if payment method is CARD
     */
    private void validateCardDetails(PosPlaceOrderRequest request) {
        log.debug("Validating card details for payment method: {}", request.getPaymentMethodId());

        if (request.getCardLastFourDigits() != null) {
            String lastFour = request.getCardLastFourDigits();

            if (!FOUR_DIGITS_PATTERN.matcher(lastFour).matches()) {
                log.error("Invalid card last four digits: {}", lastFour);
                throw new InvalidCardDetailsException(
                        "Card last four digits must be exactly 4 digits, received: " + lastFour);
            }

            log.debug("Card details validation passed");
        }
    }

    /**
     * Step 5: Validate customer exists via RabbitMQ
     */
    private void validateCustomer(Long customerId) {
        log.debug("Validating customer exists: {}", customerId);

        try {
            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            Map<String, Object> request = Map.of(
                    "customerIds", java.util.Collections.singletonList(customerId),
                    "includeAddresses", false
            );

            Object response = rabbitTemplate.convertSendAndReceive(
                    CUSTOMER_DETAILS_QUEUE,
                    request
            );

            if (response == null) {
                log.error("Customer validation returned null: {}", customerId);
                throw new CustomerNotFoundException("Customer not found: " + customerId);
            }

            log.debug("Customer validation passed: {}", customerId);

        } catch (CustomerNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate customer {}: {}", customerId, e.getMessage());
            throw new CustomerNotFoundException("Failed to validate customer: " + customerId);
        }
    }

    /**
     * Step 6: Final real-time stock validation for all cart items
     * Prevents race conditions where stock may have changed
     */
    private void validateStockAvailability(PosCartEntity cart) {
        List<PosCartItemEntity> items = cartItemRepository.findByCartId(cart.getId());

        log.debug("Performing final stock validation for {} items", items.size());

        for (PosCartItemEntity item : items) {
            boolean hasStock = stockValidationService.validateStock(
                    item.getProductId(),
                    item.getVariantId(),
                    item.getQuantity()
            );

            if (!hasStock) {
                log.error("Insufficient stock for product: {}, variant: {}, requested: {}, available: {}",
                        item.getProductId(), item.getVariantId(), item.getQuantity(), item.getStockAvailable());
                throw new InsufficientStockException(
                        String.format("Insufficient stock for product %d (variant %d): requested %d, available %d",
                                item.getProductId(), item.getVariantId(), item.getQuantity(), item.getStockAvailable()));
            }
        }

        log.debug("Stock validation passed for all {} items", items.size());
    }

    /**
     * Create order after all validations passed
     */
    private PosOrderResponse createOrder(PosCartEntity cart, PosPlaceOrderRequest request) {
        log.debug("Creating order for cart: {}", cart.getUuid());

        OrderEntity order = orderCreationService.createPosOrder(cart, request);
        cleanupCartAfterCheckout(cart);

        String receiptNumber = receiptRepository.findByOrderId(order.getId())
                .map(receipt -> receipt.getReceiptNumber())
                .orElse(order.getOrderNumber());

        String terminalCode = terminalRepository.findById(cart.getTerminalId())
                .map(PosTerminalEntity::getCode)
                .orElse("UNKNOWN");

        String cashierName = cashierRepository.findById(cart.getCashierId())
                .map(PosCashierEntity::getName)
                .orElse("UNKNOWN");

        return PosOrderResponse.builder()
                .orderUuid(order.getUuid())
                .orderNumber(order.getOrderNumber())
                .receiptNumber(receiptNumber)
                .terminalCode(terminalCode)
                .cashierName(cashierName)
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .subtotal(order.getSubtotal())
                .discountTotal(order.getDiscountTotal())
                .shippingCost(order.getShippingCost())
                .total(order.getTotal())
                .paymentMethodCode("POS")
                .paymentMethodName("POS Payment")
                .createdAt(order.getCreatedAt())
                .message("Order placed successfully")
                .build();
    }

    /**
     * Clean up cart after successful checkout
     * Deletes all cart items, resets cart totals, and clears Redis cache
     * Cart remains active for next customer
     */
    private void cleanupCartAfterCheckout(PosCartEntity cart) {
        log.debug("Cleaning up cart after checkout: {}", cart.getUuid());

        cartItemRepository.deleteByCartId(cart.getId());
        log.debug("Deleted all cart items for cart: {}", cart.getId());

        cart.setSubtotal(BigDecimal.ZERO);
        cart.setTaxAmount(BigDecimal.ZERO);
        cart.setTaxPercentage(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cart.setIsActive(true);
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.save(cart);
        log.debug("Reset cart totals to zero for cart: {}", cart.getUuid());

        String terminalUuid = terminalRepository.findById(cart.getTerminalId())
                .map(PosTerminalEntity::getUuid)
                .orElse(null);

        if (terminalUuid != null) {
            cartCacheService.removeCacheForTerminal(terminalUuid);
            log.info("Cleared Redis cache for terminal: {}, cart ready for next customer", terminalUuid);
        } else {
            log.warn("Could not find terminal UUID for terminalId: {}, Redis cache not cleared", cart.getTerminalId());
        }
    }
}

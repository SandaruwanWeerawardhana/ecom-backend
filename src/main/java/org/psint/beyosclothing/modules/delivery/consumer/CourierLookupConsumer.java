package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.external.CourierLookupRequest;
import org.psint.beyosclothing.modules.delivery.dto.external.CourierLookupResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierPaymentMethodMap;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.psint.beyosclothing.modules.delivery.repository.CourierPaymentMethodMapRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRateRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ Consumer for Courier Lookup
 * Handles requests from reseller module for courier information
 * Calculates shipping cost based on product weights and courier rates
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourierLookupConsumer {

    private final CourierRepository courierRepository;
    private final CourierRateRepository courierRateRepository;
    private final CourierPaymentMethodMapRepository courierPaymentMethodMapRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.product:product.exchange}")
    private String productExchange;

    @RabbitListener(queues = "${app.rabbitmq.queue.delivery-courier-lookup-request:delivery.courier.lookup.request.queue}")
    public CourierLookupResponse handleCourierLookup(CourierLookupRequest request) {
        log.info("Received courier lookup request for courier UUID: {}", request.getCourierUuid());

        try {
            Courier courier = courierRepository.findByUuid(request.getCourierUuid()).orElse(null);

            if (courier == null) {
                log.warn("Courier not found with UUID: {}", request.getCourierUuid());
                return CourierLookupResponse.builder()
                        .found(false)
                        .errorMessage("Courier not found")
                        .build();
            }

            if (!courier.getIsActive()) {
                log.warn("Courier is inactive - UUID: {}", request.getCourierUuid());
                return CourierLookupResponse.builder()
                        .found(true)
                        .courierId(courier.getId())
                        .courierUuid(courier.getUuid())
                        .courierName(courier.getName())
                        .courierCode(courier.getCode())
                        .isActive(false)
                        .errorMessage("Courier is inactive")
                        .build();
            }

            // Calculate total weight from cart items
            BigDecimal totalWeight = calculateTotalWeight(request.getCartItems());

            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Unable to calculate total weight for cart items");
                totalWeight = BigDecimal.ONE; // Default to 1kg if weight calculation fails
            }

            log.info("Total weight calculated: {} kg for {} items", totalWeight,
                    request.getCartItems() != null ? request.getCartItems().size() : 0);

            // Check if shipping is free for this payment method
            Boolean isFreeShipping = checkFreeShipping(courier.getId(), request.getPaymentMethodId());

            if (Boolean.TRUE.equals(isFreeShipping)) {
                log.info("Free shipping applied for courier {} and payment method {}",
                        courier.getUuid(), request.getPaymentMethodId());

                return CourierLookupResponse.builder()
                        .found(true)
                        .courierId(courier.getId())
                        .courierUuid(courier.getUuid())
                        .courierName(courier.getName())
                        .courierCode(courier.getCode())
                        .isActive(true)
                        .shippingCost(BigDecimal.ZERO)
                        .build();
            }

            // Find applicable courier rate
            CourierRate rate = findApplicableRate(
                    courier,
                    request.getCustomerType() != null ? request.getCustomerType() : "RESELLER",
                    request.getPaymentMethodId()
            );

            BigDecimal shippingCost;
            if (rate != null) {
                // Calculate shipping cost based on weight and rate
                shippingCost = calculateShippingCost(totalWeight, rate);
                log.info("Shipping cost calculated using courier rate: {}", shippingCost);
            } else {
                // Fallback to default cost if no rate found
                shippingCost = BigDecimal.valueOf(300);
                log.warn("No applicable courier rate found, using default shipping cost: {}", shippingCost);
            }

            log.info("Courier lookup successful - UUID: {}, Name: {}, Shipping Cost: {}",
                    courier.getUuid(), courier.getName(), shippingCost);

            return CourierLookupResponse.builder()
                    .found(true)
                    .courierId(courier.getId())
                    .courierUuid(courier.getUuid())
                    .courierName(courier.getName())
                    .courierCode(courier.getCode())
                    .isActive(courier.getIsActive())
                    .shippingCost(shippingCost)
                    .build();

        } catch (Exception e) {
            log.error("Error processing courier lookup request", e);
            return CourierLookupResponse.builder()
                    .found(false)
                    .errorMessage("Error processing courier lookup: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Calculate total weight by fetching product details from product module
     */
    private BigDecimal calculateTotalWeight(List<CourierLookupRequest.CartItemData> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            log.warn("No cart items provided for weight calculation");
            return BigDecimal.ZERO;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;

        for (CourierLookupRequest.CartItemData item : cartItems) {
            try {
                BigDecimal itemWeight = fetchProductWeight(item.getProductId(), item.getVariantId());

                if (itemWeight != null && itemWeight.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal weightForQuantity = itemWeight.multiply(BigDecimal.valueOf(item.getQuantity()));
                    totalWeight = totalWeight.add(weightForQuantity);

                    log.debug("Product ID: {}, Variant ID: {}, Weight: {} kg, Quantity: {}, Total: {} kg",
                            item.getProductId(), item.getVariantId(), itemWeight,
                            item.getQuantity(), weightForQuantity);
                } else {
                    // Default weight if not found
                    BigDecimal defaultWeight = new BigDecimal("0.5"); // 500g default
                    totalWeight = totalWeight.add(defaultWeight.multiply(BigDecimal.valueOf(item.getQuantity())));

                    log.warn("Weight not found for Product ID: {}, Variant ID: {}, using default: {} kg",
                            item.getProductId(), item.getVariantId(), defaultWeight);
                }
            } catch (Exception e) {
                log.error("Error fetching weight for product: {}, variant: {}",
                        item.getProductId(), item.getVariantId(), e);
                // Add default weight on error
                totalWeight = totalWeight.add(new BigDecimal("0.5").multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        return totalWeight;
    }

    /**
     * Fetch product weight from product module via RabbitMQ
     */
    private BigDecimal fetchProductWeight(Long productId, Long variantId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("productId", productId);
            request.put("variantId", variantId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(3));

            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "product.details.lookup.request",
                    request
            );

            if (response != null) {
                // Handle response - could be Map or ProductDetailsResponse
                BigDecimal weight = null;

                if (response instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;

                    Boolean found = (Boolean) responseMap.get("found");
                    if (Boolean.TRUE.equals(found)) {
                        Object weightObj = responseMap.get("weight");
                        if (weightObj instanceof Number) {
                            weight = new BigDecimal(weightObj.toString());
                        }
                    }
                } else {
                    // Try reflection for cross-module compatibility
                    try {
                        Object found = response.getClass().getMethod("getFound").invoke(response);
                        if (Boolean.TRUE.equals(found)) {
                            Object weightObj = response.getClass().getMethod("getWeight").invoke(response);
                            if (weightObj instanceof BigDecimal) {
                                weight = (BigDecimal) weightObj;
                            } else if (weightObj instanceof Number) {
                                weight = new BigDecimal(weightObj.toString());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not extract weight using reflection", e);
                    }
                }

                return weight;
            }
        } catch (Exception e) {
            log.debug("Error fetching product weight via RabbitMQ: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if shipping is free for the given courier and payment method
     */
    private Boolean checkFreeShipping(Long courierId, Long paymentMethodId) {
        if (paymentMethodId == null) {
            return false;
        }

        CourierPaymentMethodMap mapping = courierPaymentMethodMapRepository
                .findByCourierIdAndPaymentMethodId(courierId, paymentMethodId)
                .orElse(null);

        return mapping != null && Boolean.TRUE.equals(mapping.getIsFeeFree());
    }

    /**
     * Find the most applicable courier rate based on customer type and payment method
     */
    private CourierRate findApplicableRate(Courier courier, String customerType, Long paymentMethodId) {
        if (courier == null) {
            return null;
        }

        CourierRate.CustomerType customerTypeEnum;
        try {
            customerTypeEnum = CourierRate.CustomerType.valueOf(customerType);
        } catch (Exception e) {
            log.warn("Invalid customer type: {}, defaulting to RESELLER", customerType);
            customerTypeEnum = CourierRate.CustomerType.RESELLER;
        }

        // Use the repository's findApplicableRates method which handles priority ordering
        List<CourierRate> applicableRates = courierRateRepository.findApplicableRates(
                courier,
                customerTypeEnum,
                paymentMethodId
        );

        // Return the first rate (highest priority)
        if (!applicableRates.isEmpty()) {
            return applicableRates.get(0);
        }

        // Fallback: Try with BOTH customer type if no specific rate found
        if (customerTypeEnum != CourierRate.CustomerType.BOTH) {
            applicableRates = courierRateRepository.findApplicableRates(
                    courier,
                    CourierRate.CustomerType.BOTH,
                    paymentMethodId
            );

            if (!applicableRates.isEmpty()) {
                return applicableRates.get(0);
            }
        }

        return null;
    }

    /**
     * Calculate shipping cost based on weight and rate
     * Same logic as ShippingCalculationConsumer
     */
    private BigDecimal calculateShippingCost(BigDecimal totalWeight, CourierRate rate) {
        BigDecimal cost;

        if (totalWeight.compareTo(BigDecimal.ONE) <= 0) {
            // Weight <= 1 kg, use first kg price
            cost = rate.getFirstKgPrice();
        } else {
            // Weight > 1 kg, calculate additional weight
            BigDecimal extraWeight = totalWeight.subtract(BigDecimal.ONE);
            long extraUnits;

            // Calculate extra units based on granularity
            switch (rate.getWeightGranularity()) {
                case PER_0_5KG:
                    extraUnits = extraWeight.divide(new BigDecimal("0.5"), 0, RoundingMode.UP).longValue();
                    break;
                case PER_0_1KG:
                    extraUnits = extraWeight.divide(new BigDecimal("0.1"), 0, RoundingMode.UP).longValue();
                    break;
                case PER_KG:
                default:
                    extraUnits = extraWeight.setScale(0, RoundingMode.UP).longValue();
                    break;
            }

            BigDecimal additionalCost = rate.getAdditionalKgPrice().multiply(new BigDecimal(extraUnits));
            cost = rate.getFirstKgPrice().add(additionalCost);
        }

        // Apply min/max charge limits
        if (rate.getMinCharge() != null && cost.compareTo(rate.getMinCharge()) < 0) {
            cost = rate.getMinCharge();
        }
        if (rate.getMaxCharge() != null && cost.compareTo(rate.getMaxCharge()) > 0) {
            cost = rate.getMaxCharge();
        }

        return cost.setScale(2, RoundingMode.HALF_UP);
    }
}

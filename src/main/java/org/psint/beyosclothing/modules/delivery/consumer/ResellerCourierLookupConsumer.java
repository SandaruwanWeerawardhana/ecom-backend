package org.psint.beyosclothing.modules.delivery.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierPaymentMethodMap;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.psint.beyosclothing.modules.delivery.repository.CourierPaymentMethodMapRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRateRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.springframework.amqp.core.Message;
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
 * RabbitMQ Consumer for Reseller Courier Lookup
 * Handles courier lookup requests from reseller module
 * Calculates shipping cost based on product weights and courier rates
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerCourierLookupConsumer {

    private final CourierRepository courierRepository;
    private final CourierRateRepository courierRateRepository;
    private final CourierPaymentMethodMapRepository courierPaymentMethodMapRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.product:product.exchange}")
    private String productExchange;

    /**
     * Handles courier lookup requests from reseller module
     * Accepts Map to avoid cross-module DTO dependencies
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-courier-lookup-request:reseller.courier.lookup.request.queue}")
    public Message handleResellerCourierLookup(Message message) {

        byte[] body = message.getBody();
        Map<String, Object> requestMap;
        try {
            requestMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize courier lookup request", e);
            return createJsonMessage(buildErrorResponse("Deserialization failed",false));
        }
        log.info("=== RESELLER COURIER LOOKUP CONSUMER TRIGGERED ===");
        log.info("Received reseller courier lookup request: {}", requestMap);
        log.info("Request keys: {}", requestMap != null ? requestMap.keySet() : "null");

        try {
            // Extract request data from Map
            String courierUuid = (String) requestMap.get("courierUuid");
            String customerType = (String) requestMap.getOrDefault("customerType", "RESELLER");
            Long paymentMethodId = requestMap.get("paymentMethodId") != null ?
                    ((Number) requestMap.get("paymentMethodId")).longValue() : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cartItemsMaps = (List<Map<String, Object>>) requestMap.get("cartItems");

            log.info("Processing courier lookup for courier UUID: {}", courierUuid);

            // Find courier
            Courier courier = courierRepository.findByUuid(courierUuid).orElse(null);

            if (courier == null) {
                log.warn("Courier not found with UUID: {}", courierUuid);
                return createJsonMessage(buildErrorResponse("Courier not found", false));
            }

            if (!courier.getIsActive()) {
                log.warn("Courier is inactive - UUID: {}", courierUuid);
                return createJsonMessage(buildCourierResponse(courier, null, false, "Courier is inactive"));
            }

            // Calculate total weight from cart items
            BigDecimal totalWeight = calculateTotalWeight(cartItemsMaps);

            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Unable to calculate total weight for cart items");
                totalWeight = BigDecimal.ONE; // Default to 1kg if weight calculation fails
            }

            log.info("Total weight calculated: {} kg for {} items", totalWeight,
                    cartItemsMaps != null ? cartItemsMaps.size() : 0);

            // Check if shipping is free for this payment method
            Boolean isFreeShipping = checkFreeShipping(courier.getId(), paymentMethodId);

            if (Boolean.TRUE.equals(isFreeShipping)) {
                log.info("Free shipping applied for courier {} and payment method {}",
                        courier.getUuid(), paymentMethodId);
                return createJsonMessage(buildCourierResponse(courier, BigDecimal.ZERO, true, null));
            }

            // Find applicable courier rate
            CourierRate rate = findApplicableRate(courier, customerType, paymentMethodId);
            log.info("Applicable courier rate found: {}", rate != null ? rate.getId() : "None");
            BigDecimal shippingCost;
            if (rate != null) {
                log.info("Calculating shipping cost using courier rate - Courier UUID: {}, Rate ID: {}, Customer Type: {}, Payment Method ID: {}",
                        courier.getUuid(), rate.getId(), customerType, paymentMethodId);
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

            return createJsonMessage(buildCourierResponse(courier, shippingCost, true, null));

        } catch (Exception e) {
            log.error("Error processing reseller courier lookup request", e);
            return createJsonMessage(buildErrorResponse("Error processing courier lookup: " + e.getMessage(), false));
        }
    }

    /**
     * Calculate total weight by fetching product details from product module
     */
    private BigDecimal calculateTotalWeight(List<Map<String, Object>> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            log.warn("No cart items provided for weight calculation");
            return BigDecimal.ZERO;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Map<String, Object> item : cartItems) {
            try {
                Long productId = ((Number) item.get("productId")).longValue();
                Long variantId = ((Number) item.get("variantId")).longValue();
                Integer quantity = ((Number) item.get("quantity")).intValue();

                BigDecimal itemWeight = fetchProductWeight(productId, variantId);

                if (itemWeight != null && itemWeight.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal weightForQuantity = itemWeight.multiply(BigDecimal.valueOf(quantity));
                    totalWeight = totalWeight.add(weightForQuantity);

                    log.debug("Product ID: {}, Variant ID: {}, Weight: {} kg, Quantity: {}, Total: {} kg",
                            productId, variantId, itemWeight, quantity, weightForQuantity);
                } else {
                    // Default weight if not found
                    BigDecimal defaultWeight = new BigDecimal("0.5"); // 500g default
                    totalWeight = totalWeight.add(defaultWeight.multiply(BigDecimal.valueOf(quantity)));

                    log.warn("Weight not found for Product ID: {}, Variant ID: {}, using default: {} kg",
                            productId, variantId, defaultWeight);
                }
            } catch (Exception e) {
                log.error("Error fetching weight for item: {}", item, e);
                // Add default weight on error
                Integer quantity = item.get("quantity") != null ?
                        ((Number) item.get("quantity")).intValue() : 1;
                totalWeight = totalWeight.add(new BigDecimal("0.5").multiply(BigDecimal.valueOf(quantity)));
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

            // Set a short timeout for product lookup (3 seconds)
            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(3));

            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "product.details.lookup.request",
                    request
            );

            log.info("Response {}",response);

            if (response != null) {
                // Handle response - could be Map or ProductDetailsResponse
                BigDecimal weight = null;

                if (response instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    log.info("Received product details response: {}", responseMap);

                    Boolean found = (Boolean) responseMap.get("found");
                    if (Boolean.TRUE.equals(found)) {
                        Object weightObj = responseMap.get("weight");
                        if (weightObj == null) {
                            weightObj = responseMap.get("weightKg");
                        }
                        if (weightObj instanceof Number) {
                            weight = new BigDecimal(weightObj.toString());
                        }
                    }
                } else {
                    // Try reflection for cross-module compatibility
                    try {
                        Object found = response.getClass().getMethod("getFound").invoke(response);
                        if (Boolean.TRUE.equals(found)) {
                            Object weightObj = null;
                            // Try getWeightKg first (this is the actual method name)
                            try {
                                weightObj = response.getClass().getMethod("getWeightKg").invoke(response);
                            } catch (NoSuchMethodException e) {
                                // Fallback to getWeight if getWeightKg doesn't exist
                                weightObj = response.getClass().getMethod("getWeight").invoke(response);
                            }

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
        log.info("Finding applicable courier rate for Courier UUID: {}, Customer Type: {}, Payment Method ID: {}",
                courier.getUuid(), customerType, paymentMethodId);
        CourierRate.CustomerType customerTypeEnum;
        try {
            customerTypeEnum = CourierRate.CustomerType.valueOf(customerType);
            log.info("Customer Type: {}", customerTypeEnum);
        } catch (Exception e) {
            log.warn("Invalid customer type: {}, defaulting to RESELLER", customerType);
            customerTypeEnum = CourierRate.CustomerType.RESELLER;
        }

        // Use the repository's findApplicableRates method which handles priority ordering
        List<CourierRate> applicableRates;
        if (paymentMethodId != null) {
            applicableRates = courierRateRepository.findApplicableRates(
                    courier,
                    customerTypeEnum,
                    paymentMethodId
            );
        } else {
            applicableRates = courierRateRepository.findApplicableRates(
                    courier,
                    customerTypeEnum
            );
        }
        log.info("Applicable rates found: {}", applicableRates.size());
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
     */
    private BigDecimal calculateShippingCost(BigDecimal totalWeight, CourierRate rate) {
        BigDecimal cost;
        log.info("Calculating shipping cost - Total Weight: {} kg, Rate ID: {}, First Kg Price: {}, Additional Kg Price: {}, Granularity: {}",
                totalWeight, rate.getId(), rate.getFirstKgPrice(), rate.getAdditionalKgPrice(), rate.getWeightGranularity());
        if (totalWeight.compareTo(BigDecimal.ONE) <= 0) {
            log.info("Total Weight is less than 1 kg");
            // Weight <= 1 kg, use first kg price
            cost = rate.getFirstKgPrice();
        } else {
            log.info("Total Weight is greater than 1 kg");
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
                    extraUnits = extraWeight.setScale(0, RoundingMode.UP).longValue();
                    break;
                default:
                    extraUnits = extraWeight.setScale(0, RoundingMode.UP).longValue();
                    break;
            }

            BigDecimal additionalCost = rate.getAdditionalKgPrice().multiply(new BigDecimal(extraUnits));
            cost = rate.getFirstKgPrice().add(additionalCost);
        }

        // Apply min/max charge limits
//        if (rate.getMinCharge() != null && cost.compareTo(rate.getMinCharge()) < 0) {
//            cost = rate.getMinCharge();
//        }
//        if (rate.getMaxCharge() != null && cost.compareTo(rate.getMaxCharge()) > 0) {
//            cost = rate.getMaxCharge();
//        }
        log.info("Shipping cost: {}", cost);
        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Build successful courier response
     */
    private Map<String, Object> buildCourierResponse(Courier courier, BigDecimal shippingCost,
                                                     boolean isActive, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("courierId", courier.getId());
        response.put("courierUuid", courier.getUuid());
        response.put("courierName", courier.getName());
        response.put("courierCode", courier.getCode());
        response.put("isActive", isActive);
        response.put("shippingCost", shippingCost);
        if (errorMessage != null) {
            response.put("errorMessage", errorMessage);
        }
        return response;
    }

    /**
     * Build error response
     */
    private Map<String, Object> buildErrorResponse(String errorMessage, boolean found) {
        Map<String, Object> response = new HashMap<>();
        response.put("found", found);
        response.put("errorMessage", errorMessage);
        return response;
    }

    private Message createJsonMessage(Map<String, Object> response) {
        try {
            byte[] jsonBody = objectMapper.writeValueAsBytes(response);
            org.springframework.amqp.core.MessageProperties props = new org.springframework.amqp.core.MessageProperties();
            props.setContentType("application/json");
            props.setContentEncoding("UTF-8");
            props.setContentLength(jsonBody.length);
            // CRITICAL: __TypeId__ header required for Jackson2JsonMessageConverter to deserialize the reply
            props.setHeader("__TypeId__", response.getClass().getName());
            return new org.springframework.amqp.core.Message(jsonBody, props);
        } catch (Exception e) {
            log.error("Error converting response to JSON message", e);
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

}

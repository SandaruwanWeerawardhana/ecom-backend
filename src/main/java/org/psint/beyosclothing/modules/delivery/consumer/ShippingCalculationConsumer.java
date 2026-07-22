package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.shared.dto.ShippingCalculationRequest;
import org.psint.beyosclothing.shared.dto.ShippingCalculationResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierPaymentMethodMap;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.psint.beyosclothing.modules.delivery.repository.CourierPaymentMethodMapRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRateRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ Consumer for Shipping Cost Calculation
 * Handles requests from Payment module for checkout page
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingCalculationConsumer {

    private final CourierRepository courierRepository;
    private final CourierRateRepository courierRateRepository;
    private final CourierPaymentMethodMapRepository courierPaymentMethodMapRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.delivery-shipping-calculate-request:delivery.shipping.calculate.request.queue}")
    public ShippingCalculationResponse handleShippingCalculation(ShippingCalculationRequest request) {
        log.info("Received shipping calculation request - Request ID: {}, Courier: {}, Weight: {} kg, Customer Type: {}, Payment Method: {}",
                request.getRequestId(), request.getCourierUuid(), request.getTotalWeight(),
                request.getCustomerType(), request.getPaymentMethodId());

        try {
            // Find courier
            Courier courier = courierRepository.findByUuid(request.getCourierUuid()).orElse(null);

            if (courier == null || !courier.getIsActive()) {
                log.warn("Courier not found or inactive - ID: {}", request.getCourierUuid());
                return ShippingCalculationResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Courier not found or inactive")
                        .build();
            }

            // Check if shipping is free for this payment method
            Boolean isFreeShipping = checkFreeShipping(courier.getId(), request.getPaymentMethodId());

            if (Boolean.TRUE.equals(isFreeShipping)) {
                log.info("Free shipping applied for courier {} and payment method {}",
                        request.getCourierUuid(), request.getPaymentMethodId());

                Map<String, Object> breakdown = new HashMap<>();
                breakdown.put("message", "Free shipping applied");
                breakdown.put("original_cost", 0.00);

                return ShippingCalculationResponse.builder()
                        .requestId(request.getRequestId())
                        .found(true)
                        .courierId(courier.getId())
                        .courierName(courier.getName())
                        .shippingCost(BigDecimal.ZERO)
                        .totalWeight(request.getTotalWeight())
                        .isFree(true)
                        .breakdown(breakdown)
                        .build();
            }

            // Find applicable courier rate
            CourierRate rate = findApplicableRate(
                    courier,
                    request.getCustomerType(),
                    request.getPaymentMethodId()
            );

            if (rate == null) {
                List<CourierRate> allRatesForCourier = courierRateRepository.findByCourier(courier);
                log.warn("No applicable courier rate found for courier {}, customer type {}, payment method {}. "
                                + "Total rates configured for this courier: {}",
                        request.getCourierUuid(), request.getCustomerType(), request.getPaymentMethodId(),
                        allRatesForCourier.size());
                return ShippingCalculationResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("No applicable shipping rate found")
                        .build();
            }

            // Calculate shipping cost
            BigDecimal shippingCost = calculateShippingCost(request.getTotalWeight(), rate);
            Map<String, Object> breakdown = buildBreakdown(request.getTotalWeight(), rate, shippingCost);

            log.info("Shipping cost calculated - Request ID: {}, Cost: {}, Weight: {} kg",
                    request.getRequestId(), shippingCost, request.getTotalWeight());

            return ShippingCalculationResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .courierId(courier.getId())
                    .courierName(courier.getName())
                    .shippingCost(shippingCost)
                    .totalWeight(request.getTotalWeight())
                    .isFree(false)
                    .breakdown(breakdown)
                    .build();

        } catch (Exception e) {
            log.error("Error calculating shipping cost - Request ID: {}", request.getRequestId(), e);
            return ShippingCalculationResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .errorMessage("Error calculating shipping cost: " + e.getMessage())
                    .build();
        }
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
     * Find the most applicable courier rate based on customer type and payment method.
     * Resolution is attempted from most specific to most generic so that a valid rate
     * is still returned even when the stored rows do not match the exact payment method:
     *   1. Exact customer type + payment method (or payment-method-agnostic rows)
     *   2. BOTH customer type + payment method (or payment-method-agnostic rows)
     *   3. Exact customer type, ignoring the payment method entirely
     *   4. BOTH customer type, ignoring the payment method entirely
     */
    private CourierRate findApplicableRate(Courier courier, String customerType, Long paymentMethodId) {
        if (courier == null) {
            return null;
        }

        CourierRate.CustomerType customerTypeEnum = CourierRate.CustomerType.valueOf(customerType);

        // 1. Exact customer type, matching (or null) payment method
        CourierRate rate = firstRate(courierRateRepository.findApplicableRates(courier, customerTypeEnum, paymentMethodId));
        if (rate != null) {
            return rate;
        }

        // 2. BOTH customer type, matching (or null) payment method
        if (customerTypeEnum != CourierRate.CustomerType.BOTH) {
            rate = firstRate(courierRateRepository.findApplicableRates(courier, CourierRate.CustomerType.BOTH, paymentMethodId));
            if (rate != null) {
                return rate;
            }
        }

        // 3. Exact customer type, any payment method
        rate = firstRate(courierRateRepository.findApplicableRates(courier, customerTypeEnum));
        if (rate != null) {
            log.info("Resolved courier rate by ignoring payment method {} for courier {} and customer type {}",
                    paymentMethodId, courier.getUuid(), customerTypeEnum);
            return rate;
        }

        // 4. BOTH customer type, any payment method
        if (customerTypeEnum != CourierRate.CustomerType.BOTH) {
            rate = firstRate(courierRateRepository.findApplicableRates(courier, CourierRate.CustomerType.BOTH));
            if (rate != null) {
                log.info("Resolved fallback BOTH courier rate by ignoring payment method {} for courier {}",
                        paymentMethodId, courier.getUuid());
                return rate;
            }
        }

        return null;
    }

    /**
     * Return the highest-priority rate from an ordered list, or null when empty.
     */
    private CourierRate firstRate(List<CourierRate> rates) {
        return rates.isEmpty() ? null : rates.get(0);
    }

    /**
     * Calculate shipping cost based on weight and rate
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
                    extraUnits = extraWeight.setScale(0, RoundingMode.UP).longValue();
                    log.info("Calculating extra units for PER_KG granularity - Extra Weight: {}, Extra Units: {}",
                            extraWeight, extraUnits);
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

        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Build cost breakdown for transparency
     */
    private Map<String, Object> buildBreakdown(BigDecimal totalWeight, CourierRate rate, BigDecimal totalCost) {
        Map<String, Object> breakdown = new HashMap<>();

        breakdown.put("first_kg_price", rate.getFirstKgPrice().doubleValue());
        breakdown.put("additional_kg_price", rate.getAdditionalKgPrice().doubleValue());
        breakdown.put("total_weight", totalWeight.doubleValue());
        breakdown.put("weight_granularity", rate.getWeightGranularity().name());

        if (totalWeight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraWeight = totalWeight.subtract(BigDecimal.ONE);
            breakdown.put("extra_weight", extraWeight.doubleValue());
            breakdown.put("additional_cost", totalCost.subtract(rate.getFirstKgPrice()).doubleValue());
        }

        breakdown.put("total", totalCost.doubleValue());

        if (rate.getMinCharge() != null) {
            breakdown.put("min_charge", rate.getMinCharge().doubleValue());
        }
        if (rate.getMaxCharge() != null) {
            breakdown.put("max_charge", rate.getMaxCharge().doubleValue());
        }

        return breakdown;
    }
}

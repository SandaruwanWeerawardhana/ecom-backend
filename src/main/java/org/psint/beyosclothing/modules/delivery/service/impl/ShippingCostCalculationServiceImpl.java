package org.psint.beyosclothing.modules.delivery.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupResponse;
import org.psint.beyosclothing.modules.delivery.dto.request.CalculateShippingCostRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CalculateShippingCostResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierPaymentMethodMap;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.psint.beyosclothing.modules.delivery.repository.CourierPaymentMethodMapRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRateRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.service.PaymentMethodLookupService;
import org.psint.beyosclothing.modules.delivery.service.ShippingCostCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
@Service
public class ShippingCostCalculationServiceImpl implements ShippingCostCalculationService {

    private final CourierRepository courierRepository;
    private final CourierRateRepository courierRateRepository;
    private final CourierPaymentMethodMapRepository courierPaymentMethodMapRepository;
    private final PaymentMethodLookupService paymentMethodLookupService; // RabbitMQ-based lookup

    @Override
    @Transactional(readOnly = true)
    public CalculateShippingCostResponse calculateShippingCost(CalculateShippingCostRequest request) {
        log.info("Calculating shipping cost for courier: {}, weight: {}, customerType: {}, paymentMethod: {}",
                request.getCourierUuid(), request.getTotalWeight(), request.getCustomerType(), request.getPaymentMethodId());

        // 1. Validate and fetch courier
        Courier courier = courierRepository.findByUuid(request.getCourierUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with UUID: " + request.getCourierUuid()));

        if (!Boolean.TRUE.equals(courier.getIsActive())) {
            throw new ResourceNotFoundException("Courier is not active: " + request.getCourierUuid());
        }

        // 2. Check if shipping is free due to courier-payment method mapping
        if (request.getPaymentMethodId() != null) {
            boolean isFeeFreeFromMap = checkCourierPaymentMethodMap(courier, request.getPaymentMethodId());
            if (isFeeFreeFromMap) {
                log.info("Shipping is free due to courier-payment method mapping");
                return buildFreeShippingResponse(courier, request, "Free shipping - Courier payment method configuration");
            }

            // 3. Check if payment method itself provides free courier fee
            boolean isFeeFreeFromPaymentMethod = checkPaymentMethodFeeFree(request.getPaymentMethodId());
            if (isFeeFreeFromPaymentMethod) {
                log.info("Shipping is free due to payment method configuration");
                return buildFreeShippingResponse(courier, request, "Free shipping - Payment method configuration");
            }
        }

        // 4. Find applicable courier rate
        CourierRate courierRate = findApplicableCourierRate(courier, request.getCustomerType(), request.getPaymentMethodId());

        // 5. Calculate cost based on weight and granularity
        BigDecimal calculatedCost = calculateCostFromRate(courierRate, request.getTotalWeight());

        // 6. Apply min/max charge constraints
        BigDecimal finalCost = applyMinMaxCharges(calculatedCost, courierRate);

        // 7. Build detailed breakdown
        Map<String, Object> breakdown = buildBreakdown(courierRate, request.getTotalWeight(), calculatedCost, finalCost);

        log.info("Calculated shipping cost: {} for weight: {}", finalCost, request.getTotalWeight());

        return CalculateShippingCostResponse.builder()
                .shippingCost(finalCost)
                .totalWeight(request.getTotalWeight())
                .isFree(false)
                .breakdown(breakdown)
                .courierName(courier.getName())
                .courierUuid(courier.getUuid())
                .courierRateId(courierRate.getId())
                .build();
    }

    private boolean checkCourierPaymentMethodMap(Courier courier, Long paymentMethodId) {
        Optional<CourierPaymentMethodMap> mapping = courierPaymentMethodMapRepository
                .findByCourierAndPaymentMethodId(courier, paymentMethodId);

        return mapping.isPresent() && Boolean.TRUE.equals(mapping.get().getIsFeeFree());
    }

    private boolean checkPaymentMethodFeeFree(Long paymentMethodId) {
        log.debug("Checking if payment method ID: {} provides free courier fee via RabbitMQ", paymentMethodId);

        // Use RabbitMQ-based lookup to get payment method details from Payment module
        PaymentMethodLookupResponse response = paymentMethodLookupService.lookupPaymentMethodById(paymentMethodId);

        if (response == null || !response.isFound()) {
            log.warn("Payment method not found via RabbitMQ for ID: {}", paymentMethodId);
            return false;
        }

        boolean isFree = Boolean.TRUE.equals(response.getIsCourierFeeFree());
        log.debug("Payment method ID: {} has isCourierFeeFree: {}", paymentMethodId, isFree);

        return isFree;
    }

    private CourierRate findApplicableCourierRate(Courier courier, CourierRate.CustomerType customerType, Long paymentMethodId) {
        log.debug("Finding applicable courier rate for courier: {}, customerType: {}, paymentMethodId: {}",
                courier.getId(), customerType, paymentMethodId);

        // Try to find rates using the query that handles priority
        List<CourierRate> rates = courierRateRepository.findApplicableRates(courier, customerType, paymentMethodId);

        if (!rates.isEmpty()) {
            log.debug("Found {} applicable rates, using the first one", rates.size());
            return rates.getFirst();
        }

        // Fallback: Try BOTH customer type
        if (customerType != CourierRate.CustomerType.BOTH) {
            log.debug("No rate found for specific customer type, trying BOTH");
            rates = courierRateRepository.findApplicableRates(courier, CourierRate.CustomerType.BOTH, paymentMethodId);
            if (!rates.isEmpty()) {
                return rates.getFirst();
            }
        }

        throw new ResourceNotFoundException(
                String.format("No applicable courier rate found for courier: %s, customerType: %s, paymentMethodId: %s",
                        courier.getId(), customerType, paymentMethodId));
    }

    private BigDecimal calculateCostFromRate(CourierRate rate, BigDecimal weight) {
        log.debug("Calculating cost for weight: {} using rate: {}", weight, rate.getId());

        BigDecimal firstKgPrice = rate.getFirstKgPrice();
        BigDecimal additionalKgPrice = rate.getAdditionalKgPrice();

        if (firstKgPrice == null) {
            firstKgPrice = BigDecimal.ZERO;
        }
        if (additionalKgPrice == null) {
            additionalKgPrice = BigDecimal.ZERO;
        }

        // If weight <= 1kg, return first kg price
        if (weight.compareTo(BigDecimal.ONE) <= 0) {
            log.debug("Weight <= 1kg, returning first kg price: {}", firstKgPrice);
            return firstKgPrice;
        }

        // Calculate extra weight
        BigDecimal extraWeight = weight.subtract(BigDecimal.ONE);
        log.debug("Extra weight: {}", extraWeight);

        // Calculate extra units based on granularity
        BigDecimal extraUnits = calculateExtraUnits(extraWeight, rate.getWeightGranularity());
        log.debug("Extra units (after granularity): {}", extraUnits);

        // Calculate total cost
        BigDecimal additionalCost = extraUnits.multiply(additionalKgPrice);
        BigDecimal totalCost = firstKgPrice.add(additionalCost);

        log.debug("First kg price: {}, Additional cost: {}, Total cost: {}",
                firstKgPrice, additionalCost, totalCost);

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExtraUnits(BigDecimal extraWeight, CourierRate.WeightGranularity granularity) {
        if (granularity == null) {
            granularity = CourierRate.WeightGranularity.PER_KG;
        }

        BigDecimal divisor = switch (granularity) {
            case PER_0_1KG -> new BigDecimal("0.1");
            case PER_0_5KG -> new BigDecimal("0.5");
            case PER_KG -> BigDecimal.ONE;
        };

        // Round up to nearest unit
        BigDecimal units = extraWeight.divide(divisor, 0, RoundingMode.UP);
        log.debug("Granularity: {}, Divisor: {}, Units: {}", granularity, divisor, units);

        return units;
    }

    private BigDecimal applyMinMaxCharges(BigDecimal calculatedCost, CourierRate rate) {
        BigDecimal finalCost = calculatedCost;

        // Apply minimum charge
        if (rate.getMinCharge() != null && finalCost.compareTo(rate.getMinCharge()) < 0) {
            log.debug("Applying minimum charge: {} (calculated: {})", rate.getMinCharge(), calculatedCost);
            finalCost = rate.getMinCharge();
        }

        // Apply maximum charge
        if (rate.getMaxCharge() != null && finalCost.compareTo(rate.getMaxCharge()) > 0) {
            log.debug("Applying maximum charge: {} (calculated: {})", rate.getMaxCharge(), calculatedCost);
            finalCost = rate.getMaxCharge();
        }

        return finalCost.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> buildBreakdown(CourierRate rate, BigDecimal weight, BigDecimal calculatedCost, BigDecimal finalCost) {
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("totalWeight", weight);
        breakdown.put("firstKgPrice", rate.getFirstKgPrice());
        breakdown.put("additionalKgPrice", rate.getAdditionalKgPrice());
        breakdown.put("weightGranularity", rate.getWeightGranularity().name());
        breakdown.put("calculatedCost", calculatedCost);
        breakdown.put("minCharge", rate.getMinCharge());
        breakdown.put("maxCharge", rate.getMaxCharge());
        breakdown.put("finalCost", finalCost);
        breakdown.put("customerType", rate.getCustomerType().name());
        breakdown.put("paymentMethodId", rate.getPaymentMethodId());

        if (weight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraWeight = weight.subtract(BigDecimal.ONE);
            breakdown.put("extraWeight", extraWeight);
            BigDecimal extraUnits = calculateExtraUnits(extraWeight, rate.getWeightGranularity());
            breakdown.put("extraUnits", extraUnits);
        }

        return breakdown;
    }

    private CalculateShippingCostResponse buildFreeShippingResponse(Courier courier, CalculateShippingCostRequest request, String reason) {
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("totalWeight", request.getTotalWeight());
        breakdown.put("reason", reason);

        return CalculateShippingCostResponse.builder()
                .shippingCost(BigDecimal.ZERO)
                .totalWeight(request.getTotalWeight())
                .isFree(true)
                .freeReason(reason)
                .breakdown(breakdown)
                .courierName(courier.getName())
                .courierUuid(courier.getUuid())
                .build();
    }
}

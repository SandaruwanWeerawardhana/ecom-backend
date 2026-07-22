package org.psint.beyosclothing.modules.delivery.service;

import org.psint.beyosclothing.modules.delivery.dto.request.CalculateShippingCostRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CalculateShippingCostResponse;

public interface ShippingCostCalculationService {
    /**
     * Calculate shipping cost based on weight, courier, payment method, and customer type
     *
     * @param request Calculation request containing all necessary parameters
     * @return Calculated shipping cost with detailed breakdown
     */
    CalculateShippingCostResponse calculateShippingCost(CalculateShippingCostRequest request);
}

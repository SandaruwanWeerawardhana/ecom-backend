package org.psint.beyosclothing.modules.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;

import java.math.BigDecimal;

/**
 * Request DTO for calculating shipping costs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalculateShippingCostRequest {

    @NotNull(message = "Total weight is required")
    @DecimalMin(value = "0.001", message = "Total weight must be greater than zero")
    private BigDecimal totalWeight;

    @NotBlank(message = "Courier UUID is required")
    private String courierUuid;

    private Long paymentMethodId;

    @NotNull(message = "Customer type is required")
    private CourierRate.CustomerType customerType;
}


package org.psint.beyosclothing.modules.orders.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for adding pickup requests to Koombiyo
 * Used by admin to request courier pickup from a location
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPickupRequestDto {

    /**
     * Order UUID to request pickup for
     */
    @NotBlank(message = "Order UUID is required")
    private String orderUuid;

    /**
     * Vehicle type for pickup: Bike, Three wheel, Lorry
     */
    @NotBlank(message = "Vehicle type is required")
    @Size(min = 1, max = 50, message = "Vehicle type must be between 1 and 50 characters")
    private String vehicleType;

    /**
     * Pickup address/location
     */
    @NotBlank(message = "Pickup address is required")
    @Size(min = 5, max = 500, message = "Pickup address must be between 5 and 500 characters")
    private String pickupAddress;

    /**
     * Latitude coordinate for pickup location
     */
    @NotNull(message = "Latitude is required")
    private Double latitude;

    /**
     * Longitude coordinate for pickup location
     */
    @NotNull(message = "Longitude is required")
    private Double longitude;

    /**
     * Phone number for pickup contact
     */
    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 20, message = "Phone number must be between 10 and 20 characters")
    private String phone;

    /**
     * Quantity of items to be picked up
     */
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    /**
     * Pickup remarks/notes
     */
    @Size(max = 500, message = "Pickup remarks cannot exceed 500 characters")
    private String pickupRemark;
}

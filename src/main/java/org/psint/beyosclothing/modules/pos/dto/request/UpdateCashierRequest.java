package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to update a POS cashier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCashierRequest {

    private String name;

    private Long userId;

    @Pattern(regexp = "^\\d{4,6}$", message = "pinCode must be 4 to 6 digits")
    private String pinCode;

    private Boolean isActive;
}

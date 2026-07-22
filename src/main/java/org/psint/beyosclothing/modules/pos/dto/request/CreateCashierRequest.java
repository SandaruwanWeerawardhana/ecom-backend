package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to create a POS cashier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCashierRequest {

    @NotBlank(message = "Cashier name is required")
    private String name;

    // Optional link to an admin/user record
    private Long userId;

    // Optional PIN code - 4 to 6 digits if provided
    @Pattern(regexp = "^\\d{4,6}$", message = "pinCode must be 4 to 6 digits")
    private String pinCode;
}

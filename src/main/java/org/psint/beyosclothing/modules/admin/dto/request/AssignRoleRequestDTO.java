package org.psint.beyosclothing.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assign Role Request DTO
 * Used when assigning a role to an admin user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequestDTO {

    @NotNull(message = "Admin ID is required")
    private Long adminId;

    @NotBlank(message = "Role code is required")
    private String roleCode; // e.g., "SUPER_ADMIN", "MANAGER", "CASHIER"
}


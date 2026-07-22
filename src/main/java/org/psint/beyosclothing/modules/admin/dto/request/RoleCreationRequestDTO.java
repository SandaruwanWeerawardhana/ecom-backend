package org.psint.beyosclothing.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Role Creation Request DTO
 * Used when creating a new admin role
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreationRequestDTO {

    @NotBlank(message = "Role code is required")
    @Size(min = 2, max = 100, message = "Role code must be between 2 and 100 characters")
    private String roleCode; // e.g., "BRANCH_MANAGER"

    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 200, message = "Role name must be between 2 and 200 characters")
    private String roleName; // e.g., "Branch Manager"

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotEmpty(message = "At least one permission is required")
    private List<String> permissionCodes; // e.g., ["VIEW_PRODUCT", "EDIT_PRODUCT"]
}


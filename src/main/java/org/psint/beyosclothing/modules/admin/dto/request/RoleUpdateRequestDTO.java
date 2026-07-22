package org.psint.beyosclothing.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateRequestDTO {

    @Size(min = 2, max = 100, message = "Role code must be between 2 and 100 characters")
    private String roleCode; // e.g., "BRANCH_MANAGER"

    @Size(min = 2, max = 200, message = "Role name must be between 2 and 200 characters")
    private String roleName; // e.g., "Branch Manager"

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private List<String> permissionCodes; // e.g., ["VIEW_PRODUCT", "EDIT_PRODUCT"]
}

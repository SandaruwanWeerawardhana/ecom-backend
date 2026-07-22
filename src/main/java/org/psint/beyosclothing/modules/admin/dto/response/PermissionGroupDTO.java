package org.psint.beyosclothing.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Permission Group DTO
 * Groups permissions by module with parent-child hierarchy
 * Used for frontend to display permissions in a categorized tree structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionGroupDTO {

    private String module; // ADMIN, PRODUCT, ORDER, etc.
    private String moduleDisplayName; // "Admin Management", "Product Management", etc.
    private Integer displayOrder;
    private List<PermissionItemDTO> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionItemDTO {
        private Long id;
        private String permissionCode;
        private String permissionName;
        private String description;
        private Boolean isParent;
        private Integer displayOrder;
        private List<PermissionItemDTO> children; // Sub-permissions if this is a parent
    }
}

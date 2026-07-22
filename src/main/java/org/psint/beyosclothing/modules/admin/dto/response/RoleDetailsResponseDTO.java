package org.psint.beyosclothing.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Role Details Response DTO
 * Returned when retrieving role information with permissions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDetailsResponseDTO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private String userType;
    private List<PermissionDTO> permissions;
    private Integer totalPermissions;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionDTO {
        private Long id;
        private String permissionCode;
        private String permissionName;
        private String module;
        private Boolean isParent;
    }
}


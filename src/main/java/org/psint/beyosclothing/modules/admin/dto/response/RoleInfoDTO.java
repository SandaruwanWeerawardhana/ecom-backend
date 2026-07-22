package org.psint.beyosclothing.modules.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Role Info DTO
 * Lightweight DTO for role information from Auth module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;
    private String roleCode;
    private String roleName;
    private String description;
    private String userType;
}


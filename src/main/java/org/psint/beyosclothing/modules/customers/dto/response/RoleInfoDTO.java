package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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

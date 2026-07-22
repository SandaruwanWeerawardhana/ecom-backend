package org.psint.beyosclothing.modules.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * User Info DTO
 * Lightweight DTO for user information from Auth module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String userType;
    private Long userRoleId;
    private Boolean isActive;
    private Boolean emailVerified;
}


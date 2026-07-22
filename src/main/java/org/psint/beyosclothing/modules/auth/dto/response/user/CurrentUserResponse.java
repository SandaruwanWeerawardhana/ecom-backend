package org.psint.beyosclothing.modules.auth.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Current User Response DTO
 * Returns user information for authenticated requests
 * + includes admin/reseller table IDs for direct database lookups
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {
    private Long userId;
    private String uuid;
    private String email;
    private Long tableId;  // admin.id or reseller.id - the actual table ID from admin/reseller DB
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String role;
    private Boolean emailVerified;
    private Boolean isPosCashier; // For POS access control
    private String posCashierUuid; // Unique identifier for POS cashier (if applicable)
}


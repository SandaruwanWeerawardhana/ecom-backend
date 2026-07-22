package org.psint.beyosclothing.modules.auth.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal Login Response
 * Tokens are set as HTTP-only cookies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Boolean success;
    private String defaultPortal; // ADMIN | CUSTOMER | RESELLER
}


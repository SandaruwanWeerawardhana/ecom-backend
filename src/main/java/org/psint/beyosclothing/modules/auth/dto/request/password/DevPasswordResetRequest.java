package org.psint.beyosclothing.modules.auth.dto.request.password;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Development-only password reset request (NO VALIDATIONS)
 * WARNING: This should NEVER be used in production
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevPasswordResetRequest {
    private String email;
    private String newPassword;
}


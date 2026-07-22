package org.psint.beyosclothing.modules.auth.dto.request.email;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email Verification Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationRequest {

    @NotBlank(message = "Verification token is required")
    private String token;
}


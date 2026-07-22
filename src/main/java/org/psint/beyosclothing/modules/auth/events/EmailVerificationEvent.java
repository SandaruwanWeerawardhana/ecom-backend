package org.psint.beyosclothing.modules.auth.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Email Verification Event
 * Published when email verification is needed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String email;
    private String verificationToken;
    private LocalDateTime createdAt;
    private String priority; // HIGH, NORMAL, LOW
}


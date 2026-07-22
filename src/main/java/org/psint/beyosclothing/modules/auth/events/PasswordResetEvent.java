package org.psint.beyosclothing.modules.auth.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Password Reset Event
 * Published when password reset is requested
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String email;
    private String resetToken;
    private LocalDateTime createdAt;
    private String ipAddress;
}


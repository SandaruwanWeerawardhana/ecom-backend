package org.psint.beyosclothing.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Session Data stored in Redis
 * Tracks active browser sessions per user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionData implements Serializable {
    private String sessionId;
    private Long userId;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessed;
    private String userAgent;
    private String ipAddress;
}


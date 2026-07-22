package org.psint.beyosclothing.modules.auth.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Session Event for RabbitMQ
 * Published on session lifecycle events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent implements Serializable {
    private String eventType; // SESSION_CREATED | SESSION_REPLACED | SESSION_LOGGED_OUT
    private String sessionId;
    private Long userId;
    private String email;
    private String role;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String userAgent;
    private String oldSessionId; // For SESSION_REPLACED events
}


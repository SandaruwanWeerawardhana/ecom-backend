package org.psint.beyosclothing.modules.auth.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * User Created Event
 * Published when a new user is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private String phone;
    private LocalDateTime createdAt;
    private String userType;
    private Long userRoleId;
}

package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when a reseller registers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerRegisteredEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String resellerUuid;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private LocalDateTime registrationDate;
    private LocalDateTime timestamp;
}


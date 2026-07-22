package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for POS cashier
 * Note: pinCode is included because the API contract requires returning it
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCashierResponse {

    private String uuid;
    private Long userId;
    private String name;
    private String pinCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

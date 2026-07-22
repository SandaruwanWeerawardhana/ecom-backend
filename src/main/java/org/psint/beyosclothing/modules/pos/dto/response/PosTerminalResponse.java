package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for POS terminal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosTerminalResponse {
    private String uuid;
    private String name;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

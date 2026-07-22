package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for POS shift
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosShiftResponse {
    private String uuid;
    private Long cashierId; // matches PosShiftEntity.cashierId
    private Long terminalId; // matches PosShiftEntity.terminalId
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedBalance;
    private BigDecimal difference; // closingBalance - expectedBalance
    private String notes;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

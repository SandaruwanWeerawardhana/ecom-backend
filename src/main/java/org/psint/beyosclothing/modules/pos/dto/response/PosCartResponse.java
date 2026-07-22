package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POS Cart Response with full calculation breakdown
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCartResponse {
    private String uuid;
    private List<PosCartItemResponse> items;

    // Calculation breakdown
    private Double subtotal; // bigdecimal -> double for DTO
    private Double taxAmount;
    private Double taxPercentage;
    private Double discountAmount;
    private Double total;

    // POS context: use ids to match entity
    private Long cashierId;
    private Long terminalId;
    private Long customerId;
    private Boolean isActive;
    private Boolean isDraft;

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    private List<PosDraftResponse> posDraftResponses;
}

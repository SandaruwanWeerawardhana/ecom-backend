package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when admin approves a reseller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerApprovedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String resellerUuid;
    private Long userId;
    private String email;
    private LocalDateTime approvalDate;
    private Boolean allowPriceOverride;
    private BigDecimal minMarkup;
    private BigDecimal maxMarkup;
    private BigDecimal creditLimit;
    private LocalDateTime timestamp;
}


package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when reseller places an order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerOrderPlacedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderUuid;
    private String resellerUuid;
    private BigDecimal orderTotal;
    private Integer itemCount;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private LocalDateTime orderDate;
    private LocalDateTime timestamp;
}


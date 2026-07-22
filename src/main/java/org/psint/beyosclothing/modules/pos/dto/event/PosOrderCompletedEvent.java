package org.psint.beyosclothing.modules.pos.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Order Completed Event
 * <p>
 * Published when a POS order is successfully placed and completed.
 * This event is consumed by multiple downstream systems for analytics,
 * reporting, and real-time dashboards.
 * <p>
 * Consumers:
 * - Real-time sales dashboards
 * - Sales reporting and analytics
 * - Customer purchase history
 * - Inventory analytics
 * <p>
 * Design Notes:
 * - Uses @JsonInclude to support optional fields (backward/forward compatibility)
 * - ISO-8601 timestamp format for universal compatibility
 * - Immutable once published (use builder pattern)
 * - Serializable for distributed systems
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON
public class PosOrderCompletedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Event type identifier (always "POS_ORDER_COMPLETED")
     */
    private String eventType;

    /**
     * Order UUID (public identifier)
     */
    private String orderUuid;

    /**
     * Terminal/POS station ID where order was placed
     */
    private Long terminalId;

    /**
     * Cashier/employee ID who processed the order
     */
    private Long cashierId;

    /**
     * Customer ID (null for walk-in customers)
     */
    private Long customerId;

    /**
     * Total order amount (including tax, discounts, etc.)
     */
    private BigDecimal total;

    /**
     * Payment method used (e.g., "CARD", "CASH", "MOBILE")
     */
    private String paymentMethod;

    /**
     * Total number of items in the order
     */
    private Integer itemCount;

    /**
     * Timestamp when the event was created (ISO-8601 format: yyyy-MM-dd'T'HH:mm:ss'Z')
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    // ========================================
    // Optional Fields for Future Extensions
    // ========================================

    /**
     * Store/branch ID (optional, for multi-store analytics)
     */
    private Long storeId;

    /**
     * Discount amount applied (optional)
     */
    private BigDecimal discountAmount;

    /**
     * Tax amount (optional)
     */
    private BigDecimal taxAmount;

    /**
     * Order source channel (optional: POS, ONLINE, MOBILE)
     */
    private String channel;

    /**
     * Processing time in milliseconds (optional, for performance monitoring)
     */
    private Long processingTimeMs;
}

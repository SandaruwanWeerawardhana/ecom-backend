package org.psint.beyosclothing.modules.orders.repository.projection;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight projection for the POS order list. Selects only the columns the list response needs so
 * the query avoids loading full order entities - including the heavy shipping_breakdown JSON column -
 * for every matching row. The cart customer type is resolved separately (batched per page) from the
 * POS cart, which lives in a different database.
 */
public interface PosOrderListView {

    String getUuid();

    String getOrderNumber();

    OrderEntity.OrderStatus getStatus();

    OrderEntity.PaymentStatus getPaymentStatus();

    BigDecimal getSubtotal();

    BigDecimal getDiscountTotal();

    BigDecimal getShippingCost();

    BigDecimal getTotal();

    LocalDateTime getCreatedAt();

    Long getCartId();

    Long getPosTerminalId();

    Long getPosCashierId();
}

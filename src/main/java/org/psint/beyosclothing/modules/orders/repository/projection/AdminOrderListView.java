package org.psint.beyosclothing.modules.orders.repository.projection;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight projection for the admin order list. Selects only the columns the list response needs
 * so the query avoids loading full order entities - including the heavy shipping_breakdown JSON
 * column - for every matching row. The customer name is resolved separately (batched per page) from
 * the shipping address rather than joined here, because closed interface projections do not reliably
 * bind columns from an ad-hoc entity join.
 */
public interface AdminOrderListView {

    Long getId();

    String getUuid();

    String getOrderNumber();

    LocalDateTime getCreatedAt();

    BigDecimal getSubtotal();

    BigDecimal getTotal();

    OrderEntity.OrderSource getSource();

    Long getCustomerId();

    Long getResellerId();

    OrderEntity.OrderStatus getStatus();

    OrderEntity.PaymentStatus getPaymentStatus();
}

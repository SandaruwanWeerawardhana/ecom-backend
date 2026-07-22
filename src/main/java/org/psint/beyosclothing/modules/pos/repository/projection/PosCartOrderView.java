package org.psint.beyosclothing.modules.pos.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight projection for a completed POS cart shown as an order in the admin list. The customer
 * name is resolved separately (batched per page) from pos_customers by customer id rather than joined
 * here, because closed interface projections do not reliably bind columns from an ad-hoc entity join.
 */
public interface PosCartOrderView {

    String getUuid();

    LocalDateTime getCreatedAt();

    BigDecimal getSubtotal();

    BigDecimal getTotal();

    Long getCustomerId();
}

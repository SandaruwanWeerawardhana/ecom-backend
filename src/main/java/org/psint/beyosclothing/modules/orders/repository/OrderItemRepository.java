package org.psint.beyosclothing.modules.orders.repository;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(Long orderId);

    @Query("""
            SELECT oi.productId,
                   MAX(o.createdAt),
                   SUM(oi.quantity),
                   SUM(oi.totalPrice),
                   MIN(oi.unitPrice),
                   MAX(oi.unitPrice),
                   MAX(oi.productTitle)
            FROM OrderItemEntity oi
            JOIN OrderEntity o ON o.id = oi.orderId
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
              AND (:search IS NULL OR LOWER(oi.productTitle) LIKE LOWER(CONCAT('%', :search, '%')))
            GROUP BY oi.productId
            ORDER BY MAX(o.createdAt) DESC
            """)
    List<Object[]> findProductSalesForReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses,
            @Param("search") String search
    );

    @Query("""
            SELECT oi.variantId,
                   SUM(oi.quantity),
                   SUM(oi.totalPrice),
                   MIN(oi.unitPrice),
                   MAX(oi.unitPrice),
                   MAX(oi.variantTitle)
            FROM OrderItemEntity oi
            JOIN OrderEntity o ON o.id = oi.orderId
            WHERE oi.productId = :productId
              AND oi.variantId IS NOT NULL
              AND o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            GROUP BY oi.variantId
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> findVariationSalesForReport(
            @Param("productId") Long productId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses
    );

    /**
     * Aggregate items sold per hour-of-day and total revenue from order_items for single-day chart rendering.
     * Filtered and grouped purely by oi.createdAt (order_items.created_at); no order-status restriction.
     * Returns Object[]{Integer hour, Long itemsSold, BigDecimal revenue} rows ordered by hour.
     */
    @Query("""
            SELECT EXTRACT(HOUR FROM oi.createdAt),
                   SUM(oi.quantity),
                   SUM(oi.totalPrice)
            FROM OrderItemEntity oi
            WHERE oi.createdAt BETWEEN :start AND :end
            GROUP BY EXTRACT(HOUR FROM oi.createdAt)
            ORDER BY EXTRACT(HOUR FROM oi.createdAt)
            """)
    List<Object[]> aggregateHourlyItemsSold(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Aggregate items sold per calendar date and total revenue from order_items for multi-day chart rendering.
     * Filtered and grouped purely by oi.createdAt (order_items.created_at); no order-status restriction.
     * Returns Object[]{Integer year, Integer month, Integer day, Long itemsSold, BigDecimal revenue} rows ordered by date.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM oi.createdAt),
                   EXTRACT(MONTH FROM oi.createdAt),
                   EXTRACT(DAY FROM oi.createdAt),
                   SUM(oi.quantity),
                   SUM(oi.totalPrice)
            FROM OrderItemEntity oi
            WHERE oi.createdAt BETWEEN :start AND :end
            GROUP BY EXTRACT(YEAR FROM oi.createdAt), EXTRACT(MONTH FROM oi.createdAt), EXTRACT(DAY FROM oi.createdAt)
            ORDER BY EXTRACT(YEAR FROM oi.createdAt), EXTRACT(MONTH FROM oi.createdAt), EXTRACT(DAY FROM oi.createdAt)
            """)
    List<Object[]> aggregateDailyItemsSold(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Sum the quantity of order items whose order is in one of the given statuses and
     * that were created within the window. Used as the order-side items-sold count in
     * the item report summary.
     */
    @Query("""
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM OrderItemEntity oi
            JOIN OrderEntity o ON o.id = oi.orderId
            WHERE oi.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            """)
    long sumItemQuantityForReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses
    );

    /**
     * Sum the total price of order items whose order is in one of the given statuses and
     * that were created within the window. Used as the order-side item value in the item
     * report summary.
     */
    @Query("""
            SELECT COALESCE(SUM(oi.totalPrice), 0)
            FROM OrderItemEntity oi
            JOIN OrderEntity o ON o.id = oi.orderId
            WHERE oi.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            """)
    BigDecimal sumItemValueForReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses
    );
}


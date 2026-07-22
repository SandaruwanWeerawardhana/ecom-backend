package org.psint.beyosclothing.modules.orders.repository;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.projection.AdminOrderListView;
import org.psint.beyosclothing.modules.orders.repository.projection.PosOrderListView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByUuid(String uuid);

    Optional<OrderEntity> findByOrderNumber(String orderNumber);

    Optional<OrderEntity> findFirstBySourceAndCartIdOrderByCreatedAtDesc(
            OrderEntity.OrderSource source,
            Long cartId
    );

    Page<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<OrderEntity> findByResellerIdOrderByCreatedAtDesc(Long resellerId, Pageable pageable);

    Page<OrderEntity> findByStatusOrderByCreatedAtDesc(OrderEntity.OrderStatus status, Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
            SELECT o FROM OrderEntity o
            LEFT JOIN OrderShippingAddressEntity a ON a.orderId = o.id
            WHERE (:status IS NULL OR o.status = :status)
              AND (:source IS NULL OR o.source = :source)
              AND (:orderFrom IS NULL
                    OR (:orderFrom = 'CUSTOMER' AND o.customerId IS NOT NULL AND o.resellerId IS NULL)
                    OR (:orderFrom = 'RESELLER' AND o.resellerId IS NOT NULL AND o.customerId IS NULL))
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(o.orderNumber)   LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.fullName)       LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.phone)          LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.email)          LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findAllWithFilters(
            @Param("status")    OrderEntity.OrderStatus status,
            @Param("source")    OrderEntity.OrderSource source,
            @Param("orderFrom") String orderFrom,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            @Param("search")    String search,
            Pageable pageable
    );

    /**
     * Projection variant of {@link #findAllWithFilters} for the admin order list. Returns only the
     * columns the list needs instead of full order entities. The pageable limits the fetch to the
     * merge window (first pages worth of rows) so the database, not the service, bounds the result.
     */
    @Query("""
            SELECT o.id AS id,
                   o.uuid AS uuid,
                   o.orderNumber AS orderNumber,
                   o.createdAt AS createdAt,
                   o.subtotal AS subtotal,
                   o.total AS total,
                   o.source AS source,
                   o.customerId AS customerId,
                   o.resellerId AS resellerId,
                   o.status AS status,
                   o.paymentStatus AS paymentStatus
            FROM OrderEntity o
            LEFT JOIN OrderShippingAddressEntity a ON a.orderId = o.id
            WHERE (:status IS NULL OR o.status = :status)
              AND (:source IS NULL OR o.source = :source)
              AND (:orderFrom IS NULL
                    OR (:orderFrom = 'CUSTOMER' AND o.customerId IS NOT NULL AND o.resellerId IS NULL)
                    OR (:orderFrom = 'RESELLER' AND o.resellerId IS NOT NULL AND o.customerId IS NULL))
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(o.orderNumber)   LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.fullName)       LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.phone)          LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.email)          LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.createdAt DESC
            """)
    List<AdminOrderListView> findOrderViewsWithFilters(
            @Param("status")    OrderEntity.OrderStatus status,
            @Param("source")    OrderEntity.OrderSource source,
            @Param("orderFrom") String orderFrom,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            @Param("search")    String search,
            Pageable pageable
    );

    /**
     * Count companion of {@link #findOrderViewsWithFilters} so the admin list can report totals
     * without fetching every matching row.
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            LEFT JOIN OrderShippingAddressEntity a ON a.orderId = o.id
            WHERE (:status IS NULL OR o.status = :status)
              AND (:source IS NULL OR o.source = :source)
              AND (:orderFrom IS NULL
                    OR (:orderFrom = 'CUSTOMER' AND o.customerId IS NOT NULL AND o.resellerId IS NULL)
                    OR (:orderFrom = 'RESELLER' AND o.resellerId IS NOT NULL AND o.customerId IS NULL))
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(o.orderNumber)   LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.fullName)       LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.phone)          LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.email)          LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    long countOrdersWithFilters(
            @Param("status")    OrderEntity.OrderStatus status,
            @Param("source")    OrderEntity.OrderSource source,
            @Param("orderFrom") String orderFrom,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            @Param("search")    String search
    );

    @Query("""
            SELECT o FROM OrderEntity o
            LEFT JOIN OrderShippingAddressEntity a ON a.orderId = o.id
            WHERE o.resellerId = :resellerId
              AND (:status IS NULL OR o.status = :status)
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(o.orderNumber)   LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.fullName)       LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.phone)          LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.email)          LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findResellerOrdersWithFilters(
            @Param("resellerId") Long resellerId,
            @Param("search")     String search,
            @Param("status")     OrderEntity.OrderStatus status,
            @Param("startDate")  LocalDateTime startDate,
            @Param("endDate")    LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
            SELECT o FROM OrderEntity o
            LEFT JOIN OrderShippingAddressEntity a ON a.orderId = o.id
            WHERE o.resellerId = :resellerId
              AND o.status IN :statuses
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(o.orderNumber)   LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.fullName)       LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.phone)          LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.email)          LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findResellerPendingOrdersWithFilters(
            @Param("resellerId") Long resellerId,
            @Param("search")     String search,
            @Param("statuses")   java.util.List<OrderEntity.OrderStatus> statuses,
            @Param("startDate")  LocalDateTime startDate,
            @Param("endDate")    LocalDateTime endDate,
            Pageable pageable
    );

    Optional<OrderEntity> findByWayBillId(String wayBillId);

    /**
     * Lightweight projection of orders for a given source (e.g. POS) within an optional date range,
     * across all statuses. Backs the POS "list orders" endpoint: selects only the columns the list
     * needs so the query skips the heavy shipping_breakdown JSON column on every row. The id
     * tiebreaker keeps paging stable when several orders share a created_at timestamp.
     */
    @Query(value = """
            SELECT o.uuid AS uuid,
                   o.orderNumber AS orderNumber,
                   o.status AS status,
                   o.paymentStatus AS paymentStatus,
                   o.subtotal AS subtotal,
                   o.discountTotal AS discountTotal,
                   o.shippingCost AS shippingCost,
                   o.total AS total,
                   o.createdAt AS createdAt,
                   o.cartId AS cartId,
                   o.posTerminalId AS posTerminalId,
                   o.posCashierId AS posCashierId
            FROM OrderEntity o
            WHERE o.source = :source
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
            ORDER BY o.createdAt DESC, o.id DESC
            """,
            countQuery = """
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.source = :source
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt <= :endDate)
            """)
    Page<PosOrderListView> findPosOrderViews(
            @Param("source") OrderEntity.OrderSource source,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Lightweight projection of the orders linked to the given POS carts. Used to enrich the POS
     * order list (which pages over completed carts) with order number and statuses for the carts
     * that produced an order row (delivery orders); walk-in checkouts have no matching order.
     * Filtered by source because cart_id values from other modules' cart tables can collide with
     * POS cart ids, and ordered newest-first so callers keeping the first row per cart get the
     * latest order.
     */
    @Query("""
            SELECT o.uuid AS uuid,
                   o.orderNumber AS orderNumber,
                   o.status AS status,
                   o.paymentStatus AS paymentStatus,
                   o.subtotal AS subtotal,
                   o.discountTotal AS discountTotal,
                   o.shippingCost AS shippingCost,
                   o.total AS total,
                   o.createdAt AS createdAt,
                   o.cartId AS cartId,
                   o.posTerminalId AS posTerminalId,
                   o.posCashierId AS posCashierId
            FROM OrderEntity o
            WHERE o.cartId IN :cartIds
              AND o.source = :source
            ORDER BY o.createdAt DESC
            """)
    java.util.List<PosOrderListView> findPosOrderViewsByCartIds(
            @Param("cartIds") java.util.List<Long> cartIds,
            @Param("source") OrderEntity.OrderSource source
    );

    // ── RESELLER DASHBOARD METRICS QUERIES ──────────────────────────────────

    /**
     * Get total sales (sum of order totals) for a reseller
     * @param resellerId The reseller ID
     * @return Total sales amount, or null if no completed orders
     */
    @Query("""
            SELECT COALESCE(SUM(o.total), 0)
            FROM OrderEntity o
            WHERE o.resellerId = :resellerId
              AND o.status = 'COMPLETED'
            """)
    java.math.BigDecimal getTotalSalesForReseller(@Param("resellerId") Long resellerId);

    /**
     * Count total orders for a reseller
     * @param resellerId The reseller ID
     * @return Total order count
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.resellerId = :resellerId
            """)
    Long countByResellerId(@Param("resellerId") Long resellerId);

    /**
     * Count pending orders for a reseller (PENDING, PROCESSING, OUT_FOR_DELIVERY)
     * @param resellerId The reseller ID
     * @return Count of pending orders
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.resellerId = :resellerId
              AND o.status IN ('PENDING', 'PROCESSING', 'OUT_FOR_DELIVERY')
            """)
    Long countPendingOrdersByResellerId(@Param("resellerId") Long resellerId);

    /**
     * Count total orders for a customer.
     * @param customerId The customer ID
     * @return Total order count
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.customerId = :customerId
            """)
    Long countByCustomerId(@Param("customerId") Long customerId);

    /**
     * Count pending orders for a customer (PENDING, PROCESSING, OUT_FOR_DELIVERY).
     * @param customerId The customer ID
     * @return Count of pending orders
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.customerId = :customerId
              AND o.status IN ('PENDING', 'PROCESSING', 'OUT_FOR_DELIVERY')
            """)
    Long countPendingOrdersByCustomerId(@Param("customerId") Long customerId);

    /**
     * Count completed orders for a customer.
     * @param customerId The customer ID
     * @return Count of completed orders
     */
    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.customerId = :customerId
              AND o.status = 'COMPLETED'
            """)
    Long countCompletedOrdersByCustomerId(@Param("customerId") Long customerId);

    /**
     * Get recent orders for a reseller (latest N orders)
     * @param resellerId The reseller ID
     * @param pageable Pagination information
     * @return List of recent order entities
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.resellerId = :resellerId
            ORDER BY o.createdAt DESC
            """)
    java.util.List<OrderEntity> findRecentOrdersByResellerId(
            @Param("resellerId") Long resellerId,
            org.springframework.data.domain.Pageable pageable
    );

    /**
     * Convenience method for finding recent orders by reseller with limit
     * @param resellerId The reseller ID
     * @param limit Maximum number of orders
     * @return List of recent orders limited to specified count
     */
    default java.util.List<OrderEntity> findRecentOrdersByResellerId(Long resellerId, int limit) {
        return findRecentOrdersByResellerId(
                resellerId,
                org.springframework.data.domain.PageRequest.of(0, limit)
        );
    }
    /**
     * Single-scan dashboard aggregate over active orders (PROCESSING through
     * DELIVERED/COMPLETED): the all-time order count plus the subtotal revenue for the
     * current month and the current day, computed with conditional sums so the table is
     * read once instead of once per metric. Returns a single row as
     * Object[]{Long totalCount, BigDecimal monthRevenue, BigDecimal dayRevenue}.
     * Used for the admin dashboard overview.
     */
    @Query("""
            SELECT COUNT(o),
                   COALESCE(SUM(CASE WHEN o.createdAt BETWEEN :monthStart AND :monthEnd THEN o.subtotal ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN o.createdAt BETWEEN :dayStart AND :dayEnd THEN o.subtotal ELSE 0 END), 0)
            FROM OrderEntity o
            WHERE o.status IN ('PROCESSING', 'COURIER_ORDER_PLACED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'COMPLETED')
            """)
    List<Object[]> aggregateDashboardOrderStats(
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEnd") LocalDateTime monthEnd,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Aggregate active-order counts and subtotal revenue per calendar date within the window
     * (one row per day that has orders). Returns
     * Object[]{Integer year, Integer month, Integer day, Long count, BigDecimal subtotal}.
     * Active = the fulfilment lifecycle from PROCESSING through DELIVERED/COMPLETED.
     * Used for the admin dashboard "Daily Orders This Week" chart.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM o.createdAt),
                   EXTRACT(MONTH FROM o.createdAt),
                   EXTRACT(DAY FROM o.createdAt),
                   COUNT(o),
                   COALESCE(SUM(o.subtotal), 0)
            FROM OrderEntity o
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN ('PROCESSING', 'COURIER_ORDER_PLACED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'COMPLETED')
            GROUP BY EXTRACT(YEAR FROM o.createdAt), EXTRACT(MONTH FROM o.createdAt), EXTRACT(DAY FROM o.createdAt)
            """)
    List<Object[]> aggregateDailyActiveOrderCounts(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(o)
            FROM OrderEntity o
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            """)
    Long countOrdersForReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") java.util.List<OrderEntity.OrderStatus> statuses
    );

    @Query("""
            SELECT COALESCE(SUM(o.total), 0)
            FROM OrderEntity o
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
              AND (:source IS NULL OR o.source = :source)
            """)
    java.math.BigDecimal sumOrderRevenueForReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") java.util.List<OrderEntity.OrderStatus> statuses,
            @Param("source") OrderEntity.OrderSource source
    );

    /**
     * Aggregate order revenue per hour-of-day for single-day chart rendering.
     * Returns Object[]{Integer hour, BigDecimal revenue} rows ordered by hour.
     * Time complexity: O(n) over matching orders, computed in-DB.
     */
    @Query("""
            SELECT EXTRACT(HOUR FROM o.createdAt), SUM(o.total)
            FROM OrderEntity o
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            GROUP BY EXTRACT(HOUR FROM o.createdAt)
            ORDER BY EXTRACT(HOUR FROM o.createdAt)
            """)
    List<Object[]> aggregateHourlyRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses
    );

    /**
     * Aggregate order revenue per calendar date for multi-day chart rendering.
     * Returns Object[]{Integer year, Integer month, Integer day, BigDecimal revenue} rows ordered by date.
     * Time complexity: O(n) over matching orders, computed in-DB.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM o.createdAt),
                   EXTRACT(MONTH FROM o.createdAt),
                   EXTRACT(DAY FROM o.createdAt),
                   SUM(o.total)
            FROM OrderEntity o
            WHERE o.createdAt BETWEEN :start AND :end
              AND o.status IN :statuses
            GROUP BY EXTRACT(YEAR FROM o.createdAt), EXTRACT(MONTH FROM o.createdAt), EXTRACT(DAY FROM o.createdAt)
            ORDER BY EXTRACT(YEAR FROM o.createdAt), EXTRACT(MONTH FROM o.createdAt), EXTRACT(DAY FROM o.createdAt)
            """)
    List<Object[]> aggregateDailyRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses
    );
}


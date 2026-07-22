package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.repository.projection.PosCartOrderView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * POS Cart Repository
 * Manages CRUD operations for POS carts
 */
@Repository
public interface PosCartRepository extends JpaRepository<PosCartEntity, Long> {

    /**
     * Find cart by UUID
     */
    Optional<PosCartEntity> findByUuidAndIsActiveTrue(String uuid);

    /**
     * Find cart by UUID regardless of active status (used for receipts on completed orders)
     */
    Optional<PosCartEntity> findByUuid(String uuid);

    /**
     * Find cart by UUID with pessimistic lock for updates
     * Uses native SQL to avoid Hibernate 6 generating "FOR UPDATE OF alias And is_active true"
     * which is PostgreSQL syntax and not supported by MySQL.
     */
    @Query(value = "SELECT * FROM pos_carts WHERE uuid = :uuid AND is_active=true FOR UPDATE", nativeQuery = true)
    Optional<PosCartEntity> findByUuidForUpdate(@Param("uuid") String uuid);

    /**
     * Find active carts for a specific terminal ordered by updatedAt desc.
     * Returns a list because there may be multiple active carts (defensive).
     */
    List<PosCartEntity> findByTerminalIdAndIsActiveTrueOrderByUpdatedAtDesc(Long terminalId);

    /**
     * Find all active carts for a specific cashier
     */
    List<PosCartEntity> findByCashierIdAndIsActiveTrue(Long cashierId);

    /**
     * Find all active carts for cache warmup on startup
     */
    @Query("SELECT c FROM PosCartEntity c WHERE c.isActive = true")
    List<PosCartEntity> findAllActiveCartsForWarmup();

    /**
     * Find active draft carts for a terminal ordered by updatedAt desc.
     * Used to populate draft summaries in PosCartResponse.
     */
    @Query("SELECT c FROM PosCartEntity c WHERE c.terminalId = :terminalId AND c.isActive = true AND c.isDraft = true ORDER BY c.updatedAt DESC")
    List<PosCartEntity> findDraftCartsByTerminalIdOrderByUpdatedAtDesc(@Param("terminalId") Long terminalId);

    /**
     * Find completed POS carts (checked out: not draft, not active) within an optional date range as
     * lightweight order-list projections, ordered newest first. The customer name is joined from
     * pos_customers so date and search filtering run entirely in the database. The pageable limits
     * the fetch to the admin list's merge window instead of loading every completed cart.
     */
    @Query("""
            SELECT c.uuid AS uuid,
                   c.createdAt AS createdAt,
                   c.subtotal AS subtotal,
                   c.total AS total,
                   c.customerId AS customerId
            FROM PosCartEntity c
            LEFT JOIN PosCustomerEntity cust ON cust.id = c.customerId
            WHERE c.isDraft = false
              AND c.isActive = false
              AND (:startDate IS NULL OR c.createdAt >= :startDate)
              AND (:endDate IS NULL OR c.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(cust.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.uuid)        LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    List<PosCartOrderView> findCompletedCartOrders(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Find completed POS carts (checked out: not draft, not active) within a date range, newest
     * first with an id tiebreaker for stable paging. Backs the POS "list orders" endpoint: every
     * POS sale (walk-in checkout or delivery order) ends as a completed cart, so paging over this
     * table returns all POS orders even when no row exists in the orders table. The end date is an
     * exclusive bound (start of the day after the filter's last day), avoiding the MySQL DATETIME
     * rounding that an inclusive 23:59:59.999999999 bound would trigger. Both bounds are required
     * (callers pass wide defaults when unfiltered): the previous ":param IS NULL OR ..." pattern
     * is not sargable, so MySQL scanned every completed cart instead of range-scanning
     * idx_completed_created_at.
     */
    @Query(value = """
            SELECT c
            FROM PosCartEntity c
            WHERE c.isDraft = false
              AND c.isActive = false
              AND c.createdAt >= :startDate
              AND c.createdAt < :endDateExclusive
            ORDER BY c.createdAt DESC, c.id DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM PosCartEntity c
            WHERE c.isDraft = false
              AND c.isActive = false
              AND c.createdAt >= :startDate
              AND c.createdAt < :endDateExclusive
            """)
    Page<PosCartEntity> findCompletedCarts(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive,
            Pageable pageable
    );

    /**
     * Count companion of {@link #findCompletedCartOrders} so the admin order list can report totals
     * without fetching every completed cart row.
     */
    @Query("""
            SELECT COUNT(c)
            FROM PosCartEntity c
            LEFT JOIN PosCustomerEntity cust ON cust.id = c.customerId
            WHERE c.isDraft = false
              AND c.isActive = false
              AND (:startDate IS NULL OR c.createdAt >= :startDate)
              AND (:endDate IS NULL OR c.createdAt <= :endDate)
              AND (:search IS NULL
                    OR LOWER(cust.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.uuid)        LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    long countCompletedCartOrders(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("search") String search
    );
    @Query("""
            SELECT COALESCE(SUM(c.total), 0)
            FROM PosCartEntity c
            WHERE c.createdAt BETWEEN :startDate AND :endDate
              AND c.isDraft = false
              AND c.isActive = false
            """)
    java.math.BigDecimal sumCartRevenueForReport(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Sum the subtotal of completed POS carts (checked out: not draft, not active)
     * created within the window. Used as the POS-side revenue in the item report summary.
     */
    @Query("""
            SELECT COALESCE(SUM(c.subtotal), 0)
            FROM PosCartEntity c
            WHERE c.createdAt BETWEEN :startDate AND :endDate
              AND c.isDraft = false
              AND c.isActive = false
            """)
    java.math.BigDecimal sumCartSubtotalForReport(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Count completed POS carts (checked out: not draft, not active) created within
     * the window. Used as the POS-side order count in the item report summary.
     */
    @Query("""
            SELECT COUNT(c)
            FROM PosCartEntity c
            WHERE c.createdAt BETWEEN :startDate AND :endDate
              AND c.isDraft = false
              AND c.isActive = false
            """)
    long countCartsForReport(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Single-scan dashboard aggregate over completed POS carts (checked out: not draft,
     * not active): the all-time cart count plus the subtotal revenue for the current month
     * and the current day, computed with conditional sums so the table is read once instead
     * of once per metric. Returns a single row as
     * Object[]{Long totalCount, BigDecimal monthRevenue, BigDecimal dayRevenue}.
     * Used for the admin dashboard overview (POS side).
     */
    @Query("""
            SELECT COUNT(c),
                   COALESCE(SUM(CASE WHEN c.createdAt BETWEEN :monthStart AND :monthEnd THEN c.subtotal ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN c.createdAt BETWEEN :dayStart AND :dayEnd THEN c.subtotal ELSE 0 END), 0)
            FROM PosCartEntity c
            WHERE c.isDraft = false
              AND c.isActive = false
            """)
    java.util.List<Object[]> aggregateDashboardCartStats(
            @Param("monthStart") java.time.LocalDateTime monthStart,
            @Param("monthEnd") java.time.LocalDateTime monthEnd,
            @Param("dayStart") java.time.LocalDateTime dayStart,
            @Param("dayEnd") java.time.LocalDateTime dayEnd
    );

    /**
     * Aggregate completed POS-cart counts and subtotal revenue per calendar date within the
     * window (one row per day that has carts). Returns
     * Object[]{Integer year, Integer month, Integer day, Long count, BigDecimal subtotal}.
     * Completed = checked out (not draft, not active). Mirrors the order-side daily aggregate
     * for the admin dashboard "Daily Orders This Week" chart.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM c.createdAt),
                   EXTRACT(MONTH FROM c.createdAt),
                   EXTRACT(DAY FROM c.createdAt),
                   COUNT(c),
                   COALESCE(SUM(c.subtotal), 0)
            FROM PosCartEntity c
            WHERE c.createdAt BETWEEN :startDate AND :endDate
              AND c.isDraft = false
              AND c.isActive = false
            GROUP BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt), EXTRACT(DAY FROM c.createdAt)
            """)
    List<Object[]> aggregateDailyCompletedCartCounts(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );
}



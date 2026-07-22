package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosCartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * POS Cart Item Repository
 * Manages CRUD operations for POS cart items
 */
@Repository
public interface PosCartItemRepository extends JpaRepository<PosCartItemEntity, Long> {

    /**
     * Find cart item by UUID
     */
    Optional<PosCartItemEntity> findByUuid(String uuid);

    /**
     * Find all items in a specific cart
     */
    List<PosCartItemEntity> findByCartId(Long cartId);

    /**
     * Delete all items in a specific cart
     */
    void deleteByCartId(Long cartId);

    /**
     * Find item by cartId + productId + variantId (variantId nullable)
     */
    Optional<PosCartItemEntity> findByCartIdAndProductIdAndVariantIdAndIsActiveTrue(Long cartId, Long productId, Long variantId);

    /**
     * Find item by cartId + productId when variant is null
     */
    Optional<PosCartItemEntity> findByCartIdAndProductIdAndVariantIdIsNullAndIsActive(Long cartId, Long productId,Boolean isActive);

    /**
     * Find all active items in a specific cart (is_active = true)
     */
    List<PosCartItemEntity> findByCartIdAndIsActiveTrue(Long cartId);

    /**
     * Sum the quantity of active items belonging to completed POS carts
     * (checked out: not draft, not active) created within the window.
     * Used as the POS-side items-sold count in the item report summary.
     */
    @Query("""
            SELECT COALESCE(SUM(ci.quantity), 0)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            """)
    long sumCartItemQuantityForReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Sum the total price of active items belonging to completed POS carts
     * (checked out: not draft, not active) created within the window.
     * Used as the POS-side item value in the item report summary.
     */
    @Query("""
            SELECT COALESCE(SUM(ci.totalPrice), 0)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            """)
    BigDecimal sumCartItemValueForReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Aggregate items sold per hour-of-day and total revenue from completed POS carts
     * (checked out: not draft, not active) for single-day chart rendering.
     * Grouped by the cart's created_at, mirroring the order-side hourly chart.
     * Returns Object[]{Integer hour, Long itemsSold, BigDecimal revenue} rows ordered by hour.
     */
    @Query("""
            SELECT EXTRACT(HOUR FROM c.createdAt),
                   SUM(ci.quantity),
                   SUM(ci.totalPrice)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            GROUP BY EXTRACT(HOUR FROM c.createdAt)
            ORDER BY EXTRACT(HOUR FROM c.createdAt)
            """)
    List<Object[]> aggregateHourlyItemsSoldForReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Aggregate items sold per calendar date and total revenue from completed POS carts
     * (checked out: not draft, not active) for multi-day chart rendering.
     * Grouped by the cart's created_at, mirroring the order-side daily chart.
     * Returns Object[]{Integer year, Integer month, Integer day, Long itemsSold, BigDecimal revenue} rows ordered by date.
     */
    @Query("""
            SELECT EXTRACT(YEAR FROM c.createdAt),
                   EXTRACT(MONTH FROM c.createdAt),
                   EXTRACT(DAY FROM c.createdAt),
                   SUM(ci.quantity),
                   SUM(ci.totalPrice)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            GROUP BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt), EXTRACT(DAY FROM c.createdAt)
            ORDER BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt), EXTRACT(DAY FROM c.createdAt)
            """)
    List<Object[]> aggregateDailyItemsSoldForReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Product-level POS sales aggregate over completed carts (checked out: not draft, not active)
     * created within the window. POS cart items carry no product title, so the caller resolves
     * the name via the product lookup. Returns one row per product.
     * Object[]{Long productId, LocalDateTime latestSaleAt, Long unitsSold, BigDecimal totalRevenue,
     * BigDecimal minUnitPrice, BigDecimal maxUnitPrice} ordered by latest sale desc.
     */
    @Query("""
            SELECT ci.productId,
                   MAX(c.createdAt),
                   SUM(ci.quantity),
                   SUM(ci.totalPrice),
                   MIN(ci.unitPrice),
                   MAX(ci.unitPrice)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            GROUP BY ci.productId
            ORDER BY MAX(c.createdAt) DESC
            """)
    List<Object[]> findProductSalesForReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Variant-level POS sales aggregate over completed carts (checked out: not draft, not active)
     * for one product within the window. Returns one row per variant.
     * Object[]{Long variantId, Long sold, BigDecimal totalRevenue, BigDecimal minUnitPrice, BigDecimal maxUnitPrice}
     * ordered by units sold desc.
     */
    @Query("""
            SELECT ci.variantId,
                   SUM(ci.quantity),
                   SUM(ci.totalPrice),
                   MIN(ci.unitPrice),
                   MAX(ci.unitPrice)
            FROM PosCartItemEntity ci
            JOIN PosCartEntity c ON c.id = ci.cartId
            WHERE ci.productId = :productId
              AND ci.variantId IS NOT NULL
              AND ci.isActive = true
              AND c.isDraft = false
              AND c.isActive = false
              AND c.createdAt BETWEEN :startDate AND :endDate
            GROUP BY ci.variantId
            ORDER BY SUM(ci.quantity) DESC
            """)
    List<Object[]> findVariationSalesForReport(
            @Param("productId") Long productId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}

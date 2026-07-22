package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Variant Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByUuid(String uuid);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v WHERE v.sku = :sku AND v.isActive = true")
    boolean existsActiveBySku(@Param("sku") String sku);

    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByProductIdIn(List<Long> productIds);

    List<ProductVariant> findByProductIdAndIsActive(Long productId, Boolean isActive);
}


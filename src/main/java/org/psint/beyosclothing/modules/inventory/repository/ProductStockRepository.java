package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.ProductStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Stock Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface ProductStockRepository extends JpaRepository<ProductStockEntity, Long> {

    Optional<ProductStockEntity> findByUuid(String uuid);

    Optional<ProductStockEntity> findByProductIdAndVariantId(Long productId, Long variantId);

    Optional<ProductStockEntity> findByProductIdAndVariantIdIsNull(Long productId);

    List<ProductStockEntity> findByProductId(Long productId);

    @Query("SELECT ps FROM ProductStockEntity ps WHERE ps.stockQuantity <= ps.lowStockThreshold AND ps.lowStockThreshold IS NOT NULL AND ps.isActive = true")
    List<ProductStockEntity> findLowStockProducts();

    @Query("SELECT ps FROM ProductStockEntity ps WHERE ps.stockQuantity = 0 AND ps.isActive = true")
    List<ProductStockEntity> findOutOfStockProducts();
}

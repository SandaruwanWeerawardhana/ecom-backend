package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.DamagedInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Damaged Inventory Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface DamagedInventoryRepository extends JpaRepository<DamagedInventoryEntity, Long> {

    Optional<DamagedInventoryEntity> findByUuid(String uuid);

    List<DamagedInventoryEntity> findByProductId(Long productId);

    List<DamagedInventoryEntity> findByProductIdAndVariantId(Long productId, Long variantId);

    List<DamagedInventoryEntity> findBySourceAndIsActiveTrue(DamagedInventoryEntity.DamageSource source);

    List<DamagedInventoryEntity> findByInspectedBy(Long inspectedBy);

    @Query("SELECT d FROM DamagedInventoryEntity d WHERE d.referenceReturnId = :returnId AND d.isActive = true")
    List<DamagedInventoryEntity> findByReturnId(Long returnId);

    @Query("SELECT d FROM DamagedInventoryEntity d WHERE d.referenceOrderId = :orderId AND d.isActive = true")
    List<DamagedInventoryEntity> findByOrderId(Long orderId);

    @Query("SELECT d FROM DamagedInventoryEntity d WHERE d.isActive = true ORDER BY d.dateCreated DESC")
    List<DamagedInventoryEntity> findAllActiveDamagedItems();

    @Query("SELECT SUM(d.quantity) FROM DamagedInventoryEntity d WHERE d.productId = :productId AND d.isActive = true")
    Integer getTotalDamagedQuantityByProduct(Long productId);
}


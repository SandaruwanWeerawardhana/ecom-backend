package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductPaymentMethodMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ProductPaymentMethodMapping entity
 * Database: beyos_product_db
 */
@Repository
public interface ProductPaymentMethodMappingRepository extends JpaRepository<ProductPaymentMethodMapping, Long> {

    List<ProductPaymentMethodMapping> findByProductIdAndIsActive(Long productId, Boolean isActive);

    List<ProductPaymentMethodMapping> findByProductIdIn(List<Long> productIds);

    @Query("SELECT m FROM ProductPaymentMethodMapping m WHERE m.productId IN :productIds AND m.isActive = true")
    List<ProductPaymentMethodMapping> findActiveByProductIdIn(@Param("productIds") List<Long> productIds);

    @Modifying
    @Query("DELETE FROM ProductPaymentMethodMapping m WHERE m.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    boolean existsByProductIdAndPaymentMethodId(Long productId, Long paymentMethodId);
}


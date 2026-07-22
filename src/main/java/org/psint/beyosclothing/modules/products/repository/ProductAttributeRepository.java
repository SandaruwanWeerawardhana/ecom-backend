package org.psint.beyosclothing.modules.products.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.psint.beyosclothing.modules.products.entity.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Product Attribute Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findByUuid(String uuid);

    boolean existsByName(String name);

    Optional<ProductAttribute> findByName(String name);

    @Query("SELECT COUNT(a) > 0 FROM ProductAttribute a WHERE a.name = :name AND a.isActive = true")
    boolean existsActiveByName(@Param("name") String name);
}

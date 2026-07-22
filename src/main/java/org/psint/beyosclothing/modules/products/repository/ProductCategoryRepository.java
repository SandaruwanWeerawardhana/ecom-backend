package org.psint.beyosclothing.modules.products.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.psint.beyosclothing.modules.products.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Category Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByUuid(String uuid);

    List<ProductCategory> findAllByParentId(Long parentId);

    Optional<ProductCategory> findBySlug(String slug);

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    // Check if category name exists under same parent (for main categories parentId = null)
    boolean existsByNameAndParentId(String name, Long parentId);

    @Query("SELECT COUNT(a) > 0 FROM ProductCategory a WHERE a.slug = :slug AND a.isActive = true")
    boolean existsActiveBySlug(@Param("slug") String slug);

    // Find category by name and parent
    Optional<ProductCategory> findByNameAndParentId(String name, Long parentId);

    List<ProductCategory> findByParentId(Long parentId);

    List<ProductCategory> findByParentIdAndIsActive(Long parentId, Boolean isActive);

    List<ProductCategory> findByParentIdIsNullAndIsActive(Boolean isActive);

    List<ProductCategory> findByParentIdIsNull();
}

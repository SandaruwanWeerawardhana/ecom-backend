package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductGallery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Product Gallery Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductGalleryRepository extends JpaRepository<ProductGallery, Long> {

    List<ProductGallery> findByProductIdAndIsActiveTrueOrderBySortOrderAsc(Long productId);

    List<ProductGallery> findByProductIdOrderBySortOrder(Long productId);

    void deleteByProductId(Long productId);
}

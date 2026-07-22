package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByUuid(String uuid);

    Optional<Product> findBySku(String sku);

    Optional<Product> findBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.sku = :sku AND p.isActive = true")
    boolean existsActiveBySku(@Param("sku") String sku);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.slug = :slug AND p.isActive = true")
    boolean existsActiveBySlug(@Param("slug") String slug);

    List<Product> findByCategoryId(Long categoryId);

    @Query("SELECT p FROM Product p WHERE p.isPublish = true AND p.isActive = true AND p.visibility = 'PUBLIC'")
    List<Product> findAllPublishedProducts();

    @Query("SELECT p FROM Product p WHERE p.isPublish = true AND p.isActive = true AND p.isResellerProduct = true AND p.visibility = 'PUBLIC'")
    List<Product> findAllResellerProducts();

    @Query("SELECT p FROM Product p WHERE p.featured = true AND p.isActive = true")
    List<Product> findFeaturedProducts();

    @Query("SELECT p FROM Product p WHERE p.productType = :productType AND p.isActive = true")
    List<Product> findByProductType(@Param("productType") Product.ProductType productType);

    // Count active products in a category
    Long countByCategoryIdAndIsActiveTrue(Long categoryId);

    // Search products by title and shortDescription (fallback for Elasticsearch)
    @Query("SELECT p FROM Product p WHERE p.isPublish = true AND p.isActive = true AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY p.dateCreated DESC")
    List<Product> searchByTitle(@Param("query") String query);
}

package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Product Links (Cross-sell, Upsell, Related)
 */
@Repository
public interface ProductLinkRepository extends JpaRepository<ProductLink, Long> {

    /**
     * Find all product links for a specific product
     */
    List<ProductLink> findByProductId(Long productId);

    /**
     * Find all product links by link type
     */
    List<ProductLink> findByProductIdAndLinkType(Long productId, ProductLink.LinkType linkType);

    /**
     * Delete all product links for a product
     */
    void deleteByProductId(Long productId);

    /**
     * Check if a link exists between two products
     */
    boolean existsByProductIdAndLinkedIdAndLinkType(Long productId, Long linkedId, ProductLink.LinkType linkType);
}


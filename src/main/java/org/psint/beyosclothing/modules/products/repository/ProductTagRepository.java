package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Product Tag Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    Optional<ProductTag> findByUuid(String uuid);

    Optional<ProductTag> findBySlug(String slug);

    boolean existsBySlug(String slug);
}


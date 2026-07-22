package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDimensionRepository extends JpaRepository<ProductDimension, Long> {
    Optional<ProductDimension> findByProductId(Long productId);
    Optional<ProductDimension> findByUuid(String uuid);
    void deleteByProductId(Long productId);
}


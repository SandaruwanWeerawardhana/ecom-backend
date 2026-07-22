package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductMetaRepository extends JpaRepository<ProductMeta, Long> {
    Optional<ProductMeta> findByProductId(Long productId);
    Optional<ProductMeta> findByUuid(String uuid);
    void deleteByProductId(Long productId);
}


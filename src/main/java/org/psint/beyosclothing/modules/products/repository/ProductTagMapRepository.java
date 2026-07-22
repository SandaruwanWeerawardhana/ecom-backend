package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductTagMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductTagMapRepository extends JpaRepository<ProductTagMap, Long> {
    List<ProductTagMap> findByProductId(Long productId);
    void deleteByProductId(Long productId);
    boolean existsByProductIdAndTagId(Long productId, Long tagId);
}


package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {
    List<ProductPriceHistory> findByProductIdOrderByChangedAtDesc(Long productId);
    List<ProductPriceHistory> findByVariantIdOrderByChangedAtDesc(Long variantId);
}


package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductRatingAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRatingAggregateRepository extends JpaRepository<ProductRatingAggregate, Long> {

    Optional<ProductRatingAggregate> findByUuid(String uuid);

    Optional<ProductRatingAggregate> findByProductId(Long productId);

    Boolean existsByProductId(Long productId);
}


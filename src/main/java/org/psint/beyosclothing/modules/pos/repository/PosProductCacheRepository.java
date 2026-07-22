package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosProductCacheRepository extends JpaRepository<PosProductCacheEntity, Long> {

    Page<PosProductCacheEntity> findByIsActiveTrue(Pageable pageable);

    default Optional<PosProductCacheEntity> findByProductId(Long productId) {
        return findAllByProductIdOrderByIdAsc(productId).stream().findFirst();
    }

    Optional<PosProductCacheEntity> findFirstByProductIdOrderByIdAsc(Long productId);

    List<PosProductCacheEntity> findAllByProductId(Long productId);

    List<PosProductCacheEntity> findAllByProductIdOrderByIdAsc(Long productId);

    Optional<PosProductCacheEntity> findByUuid(String uuid);
}

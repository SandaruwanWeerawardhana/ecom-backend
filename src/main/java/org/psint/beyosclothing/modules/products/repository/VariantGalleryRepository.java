package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.VariantGallery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantGalleryRepository extends JpaRepository<VariantGallery, Long> {
    List<VariantGallery> findByVariantIdOrderBySortOrder(Long variantId);
    void deleteByVariantId(Long variantId);
}


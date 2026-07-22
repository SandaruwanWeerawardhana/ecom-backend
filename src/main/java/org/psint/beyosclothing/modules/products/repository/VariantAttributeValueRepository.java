package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.VariantAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantAttributeValueRepository extends JpaRepository<VariantAttributeValue, Long> {
    List<VariantAttributeValue> findByVariantId(Long variantId);
    void deleteByVariantId(Long variantId);
}


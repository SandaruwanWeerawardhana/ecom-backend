package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Attribute Value Repository
 * Database: beyos_product_db
 */
@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    Optional<ProductAttributeValue> findByUuid(String uuid);

    List<ProductAttributeValue> findByAttributeId(Long attributeId);

    Optional<ProductAttributeValue> findByAttributeIdAndValue(Long attributeId, String value);
}


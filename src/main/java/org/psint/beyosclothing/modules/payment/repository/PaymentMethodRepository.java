package org.psint.beyosclothing.modules.payment.repository;

import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PaymentMethod entity
 * Provides CRUD operations and custom query methods
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Long> {

    Optional<PaymentMethodEntity> findByCode(String code);

    Optional<PaymentMethodEntity> findByUuid(String uuid);

    List<PaymentMethodEntity> findByType(PaymentMethodEntity.PaymentType type);

    @Query("SELECT pm FROM PaymentMethodEntity pm WHERE pm.isActive = true ORDER BY pm.name ASC")
    List<PaymentMethodEntity> findAllActive();

    @Query("SELECT pm FROM PaymentMethodEntity pm WHERE pm.type = :type AND pm.isActive = true ORDER BY pm.name ASC")
    List<PaymentMethodEntity> findAllActiveByType(@Param("type") PaymentMethodEntity.PaymentType type);

    boolean existsByCode(String code);

    boolean existsByUuid(String uuid);
}

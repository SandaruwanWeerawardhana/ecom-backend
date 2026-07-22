package org.psint.beyosclothing.modules.payment.repository;

import org.psint.beyosclothing.modules.payment.entity.PaymentMethodConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PaymentMethodConfig entity
 * Provides CRUD operations and custom query methods
 */
@Repository
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfigEntity, Long> {

    /**
     * Find all configs by method ID
     */
    List<PaymentMethodConfigEntity> findByMethodId(Long methodId);

    /**
     * Find config by method ID and config key
     */
    Optional<PaymentMethodConfigEntity> findByMethodIdAndConfigKey(Long methodId, String configKey);

    /**
     * Check if config exists by method ID and config key
     */
    boolean existsByMethodIdAndConfigKey(Long methodId, String configKey);

    /**
     * Delete all configs for a method
     */
    void deleteByMethodId(Long methodId);
}


package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosCustomerRepository extends JpaRepository<PosCustomerEntity, Long> {
    Optional<PosCustomerEntity> findByPhone(String phone);
    Optional<PosCustomerEntity> findByUuid(String uuid);

    /** Count active POS customers for dashboard metrics. */
    long countByIsActiveTrue();
}


package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ResellerBankAccount entity
 */
@Repository
public interface ResellerBankAccountRepository extends JpaRepository<ResellerBankAccount, Long> {

    List<ResellerBankAccount> findByResellerIdAndIsActive(Long resellerId, Boolean isActive);

    Optional<ResellerBankAccount> findByResellerIdAndIsPrimaryTrue(Long resellerId);

    Optional<ResellerBankAccount> findByUuid(String uuid);

    Optional<ResellerBankAccount> findByResellerIdAndUuid(Long resellerId, String uuid);
}


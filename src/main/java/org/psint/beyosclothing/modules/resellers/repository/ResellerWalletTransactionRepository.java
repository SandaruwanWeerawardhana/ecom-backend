package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerWalletTransaction;
import org.psint.beyosclothing.modules.resellers.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for ResellerWalletTransaction entity
 */
@Repository
public interface ResellerWalletTransactionRepository extends JpaRepository<ResellerWalletTransaction, Long> {

    Page<ResellerWalletTransaction> findByResellerIdOrderByDateCreatedDesc(Long resellerId, Pageable pageable);

    List<ResellerWalletTransaction> findByResellerIdAndType(Long resellerId, TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ResellerWalletTransaction t " +
           "WHERE t.resellerId = :resellerId AND t.type = :type AND t.isActive = true")
    BigDecimal calculateTotalByResellerIdAndType(@Param("resellerId") Long resellerId,
                                                   @Param("type") TransactionType type);

    Page<ResellerWalletTransaction> findByResellerIdAndTypeOrderByDateCreatedDesc(
            Long resellerId, TransactionType type, Pageable pageable);

    // Find the most recent transaction for a given withdrawal and type
    ResellerWalletTransaction findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(Long referenceWithdrawalId, TransactionType type);
}

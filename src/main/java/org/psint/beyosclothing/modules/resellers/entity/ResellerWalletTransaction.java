package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Wallet Transaction Entity
 * Tracks all wallet transactions for resellers
 */
@Entity
@Table(name = "reseller_wallet_transactions", indexes = {
    @Index(name = "idx_reseller_wallet_transactions_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_wallet_transactions_reseller_id", columnList = "reseller_id"),
    @Index(name = "idx_reseller_wallet_transactions_type", columnList = "type"),
    @Index(name = "idx_reseller_wallet_transactions_date_created", columnList = "date_created"),
    @Index(name = "idx_reseller_wallet_transactions_reference_order_id", columnList = "reference_order_id"),
    @Index(name = "idx_reseller_wallet_transactions_reseller_type", columnList = "reseller_id, type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "reseller_id", nullable = false)
    private Long resellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reference_order_id")
    private Long referenceOrderId;

    @Column(name = "reference_withdrawal_id")
    private Long referenceWithdrawalId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WalletTransactionStatus status = WalletTransactionStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = WalletTransactionStatus.PENDING;
        }
    }

    // Helper methods
    public boolean isCredit() {
        return type == TransactionType.SALE_PROFIT ||
               type == TransactionType.WITHDRAWAL_REVERSED ||
               type == TransactionType.ADMIN_CREDIT ||
               type == TransactionType.ORDER_REFUND;
    }

    public boolean isDebit() {
        return type == TransactionType.WITHDRAWAL_DEBIT ||
               type == TransactionType.ADMIN_DEBIT;
    }

    public BigDecimal getNetAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}

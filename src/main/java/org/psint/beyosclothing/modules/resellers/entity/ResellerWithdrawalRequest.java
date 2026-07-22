package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Withdrawal Request Entity
 * Manages withdrawal requests from resellers
 */
@Entity
@Table(name = "reseller_withdrawal_requests", indexes = {
    @Index(name = "idx_reseller_withdrawal_requests_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_withdrawal_requests_reseller_id", columnList = "reseller_id"),
    @Index(name = "idx_reseller_withdrawal_requests_status", columnList = "status"),
    @Index(name = "idx_reseller_withdrawal_requests_requested_date", columnList = "requested_date"),
    @Index(name = "idx_reseller_withdrawal_requests_reseller_status", columnList = "reseller_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "reseller_id", nullable = false)
    private Long resellerId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "requested_date")
    private LocalDateTime requestedDate;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "balance_before", precision = 12, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (requestedDate == null) {
            requestedDate = LocalDateTime.now();
        }
    }

    // Helper methods
    public boolean isPending() {
        return status == WithdrawalStatus.PENDING;
    }

    public boolean canBeProcessed() {
        return status == WithdrawalStatus.PENDING || status == WithdrawalStatus.APPROVED;
    }

    public boolean canBeCancelled() {
        return status == WithdrawalStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == WithdrawalStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == WithdrawalStatus.FAILED;
    }

    public boolean isRejected() {
        return status == WithdrawalStatus.REJECTED;
    }
}


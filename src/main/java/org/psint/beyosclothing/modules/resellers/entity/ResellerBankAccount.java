package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Bank Account Entity
 * Stores bank account information for resellers
 */
@Entity
@Table(name = "reseller_bank_accounts", indexes = {
    @Index(name = "idx_reseller_bank_accounts_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_bank_accounts_reseller_id", columnList = "reseller_id"),
    @Index(name = "idx_reseller_bank_accounts_is_primary", columnList = "is_primary"),
    @Index(name = "idx_reseller_bank_accounts_reseller_primary", columnList = "reseller_id, is_primary")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "reseller_id", nullable = false)
    private Long resellerId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "branch_code", length = 20)
    private String branchCode;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

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
    }

    // Helper methods
    public boolean isPrimary() {
        return Boolean.TRUE.equals(isPrimary);
    }

    public boolean isVerified() {
        return Boolean.TRUE.equals(isVerified);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        String lastFour = accountNumber.substring(accountNumber.length() - 4);
        return "****" + lastFour;
    }
}


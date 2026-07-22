package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Admin Reseller Detail Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete reseller details for admin with metrics and history")
public class AdminResellerDetailResponse {

    @Schema(description = "Reseller profile information")
    private ResellerProfile profile;

    @Schema(description = "Performance metrics")
    private PerformanceMetrics metrics;

    @Schema(description = "Recent orders")
    private List<RecentOrder> recentOrders;

    @Schema(description = "Wallet information")
    private WalletInfo wallet;

    @Schema(description = "Bank accounts")
    private List<BankAccountInfo> bankAccounts;

    @Schema(description = "Withdrawal history")
    private List<WithdrawalInfo> withdrawalHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResellerProfile {
        @Schema(description = "UUID")
        private String uuid;

        @Schema(description = "Full name")
        private String fullName;

        @Schema(description = "Email")
        private String email;

        @Schema(description = "Phone")
        private String phone;

        @Schema(description = "Address")
        private String address;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Registration date")
        private LocalDateTime registrationDate;

        @Schema(description = "Approval date")
        private LocalDateTime approvalDate;

        @Schema(description = "Is active")
        private Boolean isActive;

        @Schema(description = "Allow price override")
        private Boolean allowPriceOverride;

        @Schema(description = "Min markup %")
        private BigDecimal minMarkupPct;

        @Schema(description = "Max markup %")
        private BigDecimal maxMarkupPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        @Schema(description = "Total orders count")
        private Long totalOrders;

        @Schema(description = "Total sales amount")
        private BigDecimal totalSales;

        @Schema(description = "Total profit generated")
        private BigDecimal totalProfit;

        @Schema(description = "Average order value")
        private BigDecimal averageOrderValue;

        @Schema(description = "Orders this month")
        private Long ordersThisMonth;

        @Schema(description = "Sales this month")
        private BigDecimal salesThisMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentOrder {
        @Schema(description = "Order number")
        private String orderNumber;

        @Schema(description = "Order date")
        private LocalDateTime orderDate;

        @Schema(description = "Total amount")
        private BigDecimal total;

        @Schema(description = "Profit")
        private BigDecimal profit;

        @Schema(description = "Status")
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletInfo {
        @Schema(description = "Current balance")
        private BigDecimal currentBalance;

        @Schema(description = "Credit limit")
        private BigDecimal creditLimit;

        @Schema(description = "Available credit")
        private BigDecimal availableCredit;

        @Schema(description = "Total earned")
        private BigDecimal totalEarned;

        @Schema(description = "Total withdrawn")
        private BigDecimal totalWithdrawn;

        @Schema(description = "Pending withdrawals")
        private BigDecimal pendingWithdrawals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccountInfo {
        @Schema(description = "UUID")
        private String uuid;

        @Schema(description = "Bank name")
        private String bankName;

        @Schema(description = "Account holder")
        private String accountHolder;

        @Schema(description = "Masked account number")
        private String maskedAccountNumber;

        @Schema(description = "Is primary")
        private Boolean isPrimary;

        @Schema(description = "Is verified")
        private Boolean isVerified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalInfo {
        @Schema(description = "UUID")
        private String uuid;

        @Schema(description = "Amount")
        private BigDecimal amount;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Requested date")
        private LocalDateTime requestedDate;

        @Schema(description = "Processed date")
        private LocalDateTime processedDate;
    }
}


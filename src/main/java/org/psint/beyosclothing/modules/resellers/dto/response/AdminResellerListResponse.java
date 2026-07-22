package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Admin Reseller List Response (Paginated)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of resellers with key metrics")
public class AdminResellerListResponse {

    @Schema(description = "List of resellers")
    private List<ResellerSummary> resellers;

    @Schema(description = "Current page number")
    private Integer currentPage;

    @Schema(description = "Total pages")
    private Integer totalPages;

    @Schema(description = "Total resellers")
    private Long totalResellers;

    @Schema(description = "Page size")
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResellerSummary {
        @Schema(description = "Reseller UUID")
        private String uuid;

        @Schema(description = "Full name")
        private String fullName;

        @Schema(description = "Email")
        private String email;

        @Schema(description = "Phone")
        private String phone;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Registration date")
        private LocalDateTime registrationDate;

        @Schema(description = "Total orders count")
        private Long orderCount;

        @Schema(description = "Total sales amount")
        private BigDecimal totalSales;

        @Schema(description = "Total profit generated")
        private BigDecimal totalProfit;

        @Schema(description = "Current balance")
        private BigDecimal currentBalance;

        @Schema(description = "Is active")
        private Boolean isActive;
    }
}


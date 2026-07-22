package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for Admin Withdrawal Action Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin request to approve or reject withdrawal")
public class AdminWithdrawalActionRequest {

    @Size(max = 1000, message = "Admin notes must not exceed 1000 characters")
    @Schema(description = "Admin notes", example = "Approved and processed")
    private String notes;

    @Schema(description = "Transaction reference (if approved)", example = "TXN123456789")
    private String transactionReference;
}

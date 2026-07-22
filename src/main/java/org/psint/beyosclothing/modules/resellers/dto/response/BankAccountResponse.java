package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for Bank Account Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bank account details")
public class BankAccountResponse {

    @Schema(description = "Bank account UUID")
    private String uuid;

    @Schema(description = "Bank name", example = "Commercial Bank")
    private String bankName;

    @Schema(description = "Account holder name", example = "John Doe")
    private String accountHolderName;

    @Schema(description = "Masked account number", example = "****7890")
    private String maskedAccountNumber;

    @Schema(description = "Full account number (admin only)")
    private String accountNumber;

    @Schema(description = "Branch name", example = "Colombo Branch")
    private String branchName;

    @Schema(description = "Branch code", example = "001")
    private String branchCode;

    @Schema(description = "SWIFT code", example = "CCEYLKLX")
    private String swiftCode;

    @Schema(description = "Is primary account", example = "true")
    private Boolean isPrimary;

    @Schema(description = "Is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Is verified by admin", example = "false")
    private Boolean isVerified;

    @Schema(description = "Date created")
    private LocalDateTime dateCreated;
}


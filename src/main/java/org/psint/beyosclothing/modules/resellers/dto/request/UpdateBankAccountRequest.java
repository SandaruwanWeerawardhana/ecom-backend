package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for Updating Bank Account Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update bank account")
public class UpdateBankAccountRequest {

    @NotBlank(message = "Bank name is required")
    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    @Schema(description = "Bank name", example = "Commercial Bank")
    private String bankName;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Account holder name must not exceed 255 characters")
    @Schema(description = "Account holder name", example = "John Doe")
    private String accountHolderName;

    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    @Schema(description = "Branch name", example = "Colombo Branch")
    private String branchName;

    @Size(max = 20, message = "Branch code must not exceed 20 characters")
    @Schema(description = "Branch code", example = "001")
    private String branchCode;

    @Size(max = 20, message = "SWIFT code must not exceed 20 characters")
    @Schema(description = "SWIFT code (for international)", example = "CCEYLKLX")
    private String swiftCode;

    @Schema(description = "Set as primary account", example = "true")
    private Boolean isPrimary;
}


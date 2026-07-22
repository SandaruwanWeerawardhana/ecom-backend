package org.psint.beyosclothing.modules.resellers.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank(message = "currentPassword is required")
    private String currentPassword;

    @NotBlank(message = "newPassword is required")
    @Size(min = 5, max = 72, message = "newPassword must be between 10 and 72 characters")
    private String newPassword;

    @NotBlank(message = "confirmPassword is required")
    @Size(min = 5, max = 72, message = "confirmPassword must be between 10 and 72 characters")
    private String confirmPassword;


    @AssertTrue
    public boolean isNewPasswordConfirmed() {
        if (newPassword == null || confirmPassword == null) {
            throw new IllegalArgumentException("newPassword and confirmPassword must not be null");
        }
        return newPassword.equals(confirmPassword);
    }
}


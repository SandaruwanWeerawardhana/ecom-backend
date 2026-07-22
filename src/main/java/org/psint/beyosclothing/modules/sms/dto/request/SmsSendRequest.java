package org.psint.beyosclothing.modules.sms.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsSendRequest {

    @NotBlank(message = "Recipient phone is required")
    @Size(max = 20, message = "Recipient phone must not exceed 20 characters")
    @Pattern(regexp = "^[+0-9][0-9\\s-]{6,19}$", message = "Recipient phone must be a valid phone number")
    private String recipientPhone;

    @NotBlank(message = "Message is required")
    @Size(max = 1600, message = "Message must not exceed 1600 characters")
    private String message;
}

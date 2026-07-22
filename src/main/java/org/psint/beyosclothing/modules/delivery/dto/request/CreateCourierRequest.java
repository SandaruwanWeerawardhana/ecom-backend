package org.psint.beyosclothing.modules.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

/**
 * Create Courier Request DTO
 * Used when creating a new courier
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateCourierRequest {

    private UUID courierUuid;

    @NotBlank(message = "Courier code is required")
    @Size(min = 2, max = 50, message = "Courier code must be between 2 and 50 characters")
    private String code;

    @NotBlank(message = "Courier name is required")
    @Size(min = 2, max = 255, message = "Courier name must be between 2 and 255 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 1024, message = "API base URL must not exceed 1024 characters")
    private String apiBaseUrl;

    @Size(max = 1024, message = "API key must not exceed 1024 characters")
    private String apiKey;

    @Size(max = 15, message = "Contact phone must not exceed 50 characters")
    private String contactPhone;

    @Email(message = "Email must be valid")
    @Size(max = 30, message = "Email must not exceed 255 characters")
    private String email;

    private Boolean isActive;
}


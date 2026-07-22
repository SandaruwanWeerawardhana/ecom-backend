package org.psint.beyosclothing.modules.delivery.dto.response;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierResponse {


    @NotNull(message = "UUID cannot be null")
    private String uuid;

    @NotBlank(message = "Courier code is required")
    @Size(max = 50, message = "Courier code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Courier name is required")
    @Size(max = 255, message = "Courier name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 1024, message = "API base URL must not exceed 1024 characters")
    private String apiBaseUrl;

    @Size(max = 15, message = "Contact phone must not exceed 50 characters")
    private String contactPhone;

    @Email(message = "Email must be valid")
    @Size(max = 30, message = "Email must not exceed 255 characters")
    private String email;

    @NotNull(message = "Active status cannot be null")
    private Boolean isActive;

    @NotNull(message = "Created date cannot be null")
    private LocalDateTime createdAt;

    @NotNull(message = "Updated date cannot be null")
    private LocalDateTime updatedAt;
}


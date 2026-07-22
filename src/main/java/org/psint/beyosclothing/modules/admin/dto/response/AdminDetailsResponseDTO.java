package org.psint.beyosclothing.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Admin Details Response DTO
 * Returned when retrieving admin information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminDetailsResponseDTO {

    private Long id;
    private String uuid;
    private Long userId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private RoleInfoDTO role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

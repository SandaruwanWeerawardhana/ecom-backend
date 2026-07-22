package org.psint.beyosclothing.modules.customers.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Customer Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private String uuid;
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private List<AddressDTO> addresses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDTO {
        private Long id;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String district;
        private String country;
        private String postalCode;
        private Boolean isDefault;
    }
}


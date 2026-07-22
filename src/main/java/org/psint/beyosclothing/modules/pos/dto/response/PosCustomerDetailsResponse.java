package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCustomerDetailsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String uuid;
    private Long userId;
    private String fullName;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private Boolean isActive;
    private Integer loyaltyPoints;
    private LocalDateTime dateCreated;
    private List<CustomerAddress> addresses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAddress implements Serializable {
        private static final long serialVersionUID = 1L;

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

package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCustomerSimpleResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String uuid;
    private String fullName;
    private String phone;
    private String address;
    private String city;
    private String province;
    private String district;
    private String zipCode;
    private List<CustomerAddress> addresses;
    private String source;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAddress implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String addressLine1;
        private String addressLine2;
        private String city;
        private String province;
        private String district;
        private String country;
        private String postalCode;
        private Boolean isDefault;
    }
}


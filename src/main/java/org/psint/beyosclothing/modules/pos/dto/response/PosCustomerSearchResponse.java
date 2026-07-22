package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCustomerSearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private Boolean isActive;
    private Integer loyaltyPoints;
}

package org.psint.beyosclothing.modules.pos.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosDraftResponse {
    private String cartUuid;
    private String customerName;
    private String customerType;
    private LocalDateTime createdAt;
    private Double subTotal;
    private String status;
}

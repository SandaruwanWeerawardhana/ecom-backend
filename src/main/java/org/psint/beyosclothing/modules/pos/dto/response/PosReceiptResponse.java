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
public class PosReceiptResponse {

    private String uuid;
    private Long orderId;
    private String orderUuid;
    private String receiptNumber;
    private Integer printCount;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;
}
package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosReceiptRequest {

    @NotBlank(message = "orderUuid is required")
    private String orderUuid;
    private String receiptNumber;
    private LocalDateTime printedAt;
}

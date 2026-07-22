package org.psint.beyosclothing.modules.pos.dto.document;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSearchAnalyticsDocument {
    private String id;
    private String searchTerm;
    private LocalDateTime timestamp;
    private String terminalId;
    private String cashierId;
    private Integer resultsCount;
    private String selectedProductId;
}

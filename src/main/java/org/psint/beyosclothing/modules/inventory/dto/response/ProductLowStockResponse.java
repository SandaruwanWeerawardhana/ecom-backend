package org.psint.beyosclothing.modules.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLowStockResponse {

    private String uuId;
    private String productName;
    private String sku;
    private String attributeSummary;
    private Integer stockQuantity;
    private String thresholdLevel;
    private String alertStatus;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
}

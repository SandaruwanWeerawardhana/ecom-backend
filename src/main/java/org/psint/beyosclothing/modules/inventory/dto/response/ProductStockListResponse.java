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
public class ProductStockListResponse {

    private String id;
    private String productName;
    private String sku;
    private String attributeSummary;
    private Integer stockQuantity;
    private String inventoryStatus;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

}


package org.psint.beyosclothing.modules.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLowStockPageResponse {
    private List<ProductLowStockResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}


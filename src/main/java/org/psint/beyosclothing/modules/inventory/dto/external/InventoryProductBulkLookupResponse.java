package org.psint.beyosclothing.modules.inventory.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProductBulkLookupResponse implements Serializable {

    private String requestId;

    private Boolean success;

    private String errorMessage;

    private Map<String, InventoryProductLookupResponse> items;
}

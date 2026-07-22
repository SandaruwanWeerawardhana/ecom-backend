package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Review Image Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewImageResponse {
    private String uuid;
    private String imageUrl;
    private Integer displayOrder;
}


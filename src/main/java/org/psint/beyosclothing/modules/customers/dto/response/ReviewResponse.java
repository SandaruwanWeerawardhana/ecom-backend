package org.psint.beyosclothing.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Review Response DTO
 * Used for displaying customer reviews
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private String uuid;
    private String productUuid;
    private String productTitle;
    private CustomerBasicInfo customer;
    private BigDecimal rating;
    private String comment;
    private Boolean isApproved;
    private List<ReviewImageResponse> images;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerBasicInfo {
        private String firstName;
        private String lastName;
        private String profileImage;
    }
}


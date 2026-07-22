package org.psint.beyosclothing.modules.customers.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create Review Request DTO
 * Used when customers submit product reviews
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotBlank(message = "Product UUID is required")
    private String productUuid;

    @NotNull(message = "Rating is required")
    @DecimalMin(value = "1.0", message = "Rating must be at least 1.0")
    @DecimalMax(value = "5.0", message = "Rating must not exceed 5.0")
    private BigDecimal rating;

    private Long customerId;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;

    private List<String> imageUrls; // URLs of uploaded review images
}


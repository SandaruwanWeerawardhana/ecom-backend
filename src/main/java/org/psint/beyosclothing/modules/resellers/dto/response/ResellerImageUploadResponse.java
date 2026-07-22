package org.psint.beyosclothing.modules.resellers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned after uploading a reseller profile image.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerImageUploadResponse {

    private Boolean success;
    private String message;

    /**
     * Stored S3 object key (can be saved in DB if needed).
     */
    private String imageName;

    /**
     * Public (or presigned) URL that frontend can use to display the image.
     */
    private String imageUrl;
}


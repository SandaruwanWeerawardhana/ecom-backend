package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for AWS S3 storage operations
 */
public interface S3StorageService {

    /**
     * Upload file to S3 bucket
     * @param file The file to upload
     * @param folderPath The folder path in S3 bucket
     * @param prefix The file prefix
     * @return ImageUploadResponse containing upload details
     */
    ImageUploadResponse uploadFile(MultipartFile file, String folderPath, String prefix);

    /**
     * Upload file to S3 bucket using an explicit object key (full path + filename).
     * Useful for category images where a fixed key is required (e.g. categories/{uuid}.jpg).
     * @param file The file to upload
     * @param objectKey Full S3 object key (folder/filename.ext)
     * @return ImageUploadResponse containing upload details
     */
    ImageUploadResponse uploadFileWithKey(MultipartFile file, String objectKey);

    /**
     * Delete file from S3 bucket
     * @param fileKey The S3 object key
     */
    void deleteFile(String fileKey);

    /**
     * Get public URL for S3 object
     * @param fileKey The S3 object key
     * @return Public URL
     */
    String getPublicUrl(String fileKey);

    /**
     * Generate presigned URL for private access
     * @param fileKey The S3 object key
     * @param expirationMinutes Expiration time in minutes
     * @return Presigned URL
     */
    String generatePresignedUrl(String fileKey, int expirationMinutes);
}

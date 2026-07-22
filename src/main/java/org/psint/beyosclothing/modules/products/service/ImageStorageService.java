package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Image Storage Service
 * Handles image upload and storage for products
 */
public interface ImageStorageService {

    /**
     * Upload product thumbnail image
     */
    ImageUploadResponse uploadProductThumbnail(MultipartFile file, String productUuid);

    /**
     * Upload product category image with a fixed key pattern categories/{categoryUuid}.{ext}
     * This returns an ImageUploadResponse where imageName is the S3 object key and imageUrl
     * is the public URL (or presigned URL depending on configuration).
     */
    ImageUploadResponse uploadCategoryImage(MultipartFile file, String categoryUuid);

    /**
     * Upload product category image (wrapper for uploadCategoryImage for consistency)
     * This is an alias method to maintain consistency with controller naming
     */
    ImageUploadResponse uploadProductCategory(MultipartFile file, String categoryUuid);

    /**
     * Upload product gallery images
     */
    List<ImageUploadResponse> uploadProductGalleryImages(List<MultipartFile> files, String productUuid);

    /**
     * Upload variant thumbnail image
     */
    ImageUploadResponse uploadVariantThumbnail(MultipartFile file, String variantUuid);

    /**
     * Upload variant gallery images
     */
    List<ImageUploadResponse> uploadVariantGalleryImages(List<MultipartFile> files, String variantUuid);

    /**
     * Upload review images
     * @param files Image files to upload
     * @param reviewUuid Review UUID (can be temporary UUID during creation)
     * @return List of uploaded image responses
     */
    List<ImageUploadResponse> uploadReviewImages(List<MultipartFile> files, String reviewUuid);

    /**
     * Delete image by name
     */
    void deleteImage(String imageName, String type);

    /**
     * Get image URL by name
     */
    String getImageUrl(String imageName, String type);

    /**
     * Move product images from temp folder to final product folder
     * @param tempUuid Temporary UUID used during upload
     * @param productUuid Final product UUID
     * @param imagePath Image path with temp folder
     * @return New image path with product UUID
     */
    String moveProductImageToFinal(String tempUuid, String productUuid, String imagePath);

    /**
     * Move variant images from temp folder to final variant folder
     * @param tempUuid Temporary UUID used during upload
     * @param productUuid Product UUID
     * @param variantUuid Variant UUID
     * @param imagePath Image path with temp folder
     * @return New image path with variant UUID
     */
    String moveVariantImageToFinal(String tempUuid, String productUuid, String variantUuid, String imagePath);

    /**
     * Extract temp UUID from image path
     * @param imagePath Image path
     * @return Temp UUID or null if not found
     */
    String extractTempUuidFromPath(String imagePath);

    /**
     * Check if image path is from temp folder
     * @param imagePath Image path
     * @return true if temp folder
     */
    boolean isTempImage(String imagePath);

    /**
     * Update variant thumbnail image (delete old and upload new)
     * @param file New image file
     * @param variantUuid Variant UUID
     * @param oldImagePath Path of old image to delete
     * @return Image upload response with new image details
     */
    ImageUploadResponse updateVariantThumbnail(MultipartFile file, String variantUuid, String oldImagePath);


}
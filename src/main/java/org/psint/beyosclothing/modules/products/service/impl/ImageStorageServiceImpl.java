package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.ImageStorageService;
import org.psint.beyosclothing.modules.products.service.LocalStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Image Storage Service Implementation
 * Stores images on the local filesystem via {@link LocalStorageService}
 */
@Service("imageStorageServiceImpl")
@Slf4j
@RequiredArgsConstructor
public class ImageStorageServiceImpl implements ImageStorageService {

    private final LocalStorageService localStorageService;

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    @Override
    public ImageUploadResponse uploadProductThumbnail(MultipartFile file, String productUuid) {
        log.info("Uploading product thumbnail for product: {}", productUuid);
        validateImage(file);

        String folderPath = String.format("products/thumbnails/%s", productUuid);
        return localStorageService.uploadFile(file, folderPath, "product-thumbnail");
    }

    @Override
    public ImageUploadResponse uploadCategoryImage(MultipartFile file, String categoryUuid) {
        log.info("Uploading category image for category: {}", categoryUuid);
        validateImage(file);

        // Determine extension
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String objectKey = String.format("categories/%s%s", categoryUuid, extension);

        // Upload using explicit key so we control URL format
        return localStorageService.uploadFileWithKey(file, objectKey);
    }

    @Override
    public ImageUploadResponse uploadProductCategory(MultipartFile file, String categoryUuid) {
        // Delegate to uploadCategoryImage for consistency
        return uploadCategoryImage(file, categoryUuid);
    }

    @Override
    public List<ImageUploadResponse> uploadProductGalleryImages(List<MultipartFile> files, String productUuid) {
        log.info("Uploading {} product gallery images for product: {}", files.size(), productUuid);
        List<ImageUploadResponse> responses = new ArrayList<>();

        String folderPath = String.format("products/galleries/%s", productUuid);
        for (MultipartFile file : files) {
            validateImage(file);
            ImageUploadResponse response = localStorageService.uploadFile(file, folderPath, "product-gallery");
            responses.add(response);
        }

        return responses;
    }

    @Override
    public ImageUploadResponse uploadVariantThumbnail(MultipartFile file, String variantUuid) {
        log.info("Uploading variant thumbnail for variant: {}", variantUuid);
        validateImage(file);

        String folderPath = String.format("variants/thumbnails/%s", variantUuid);
        return localStorageService.uploadFile(file, folderPath, "variant-thumbnail");
    }

    @Override
    public List<ImageUploadResponse> uploadVariantGalleryImages(List<MultipartFile> files, String variantUuid) {
        log.info("Uploading {} variant gallery images for variant: {}", files.size(), variantUuid);
        List<ImageUploadResponse> responses = new ArrayList<>();

        String folderPath = String.format("variants/galleries/%s", variantUuid);
        for (MultipartFile file : files) {
            validateImage(file);
            ImageUploadResponse response = localStorageService.uploadFile(file, folderPath, "variant-gallery");
            responses.add(response);
        }

        return responses;
    }

    @Override
    public List<ImageUploadResponse> uploadReviewImages(List<MultipartFile> files, String reviewUuid) {
        log.info("Uploading {} review images for review: {}", files.size(), reviewUuid);
        List<ImageUploadResponse> responses = new ArrayList<>();

        String folderPath = String.format("reviews/%s", reviewUuid);
        for (MultipartFile file : files) {
            validateImage(file);
            ImageUploadResponse response = localStorageService.uploadFile(file, folderPath, "review-image");
            responses.add(response);
        }

        return responses;
    }

    @Override
    public void deleteImage(String imageName, String type) {
        // imageName contains the object key (e.g., "products/thumbnails/uuid/filename.jpg")
        localStorageService.deleteFile(imageName);
        log.info("Deleted local image: {}", imageName);
    }

    @Override
    public String getImageUrl(String imageName, String type) {
        // imageName contains the object key; build the public URL served by /uploads/**
        return localStorageService.getPublicUrl(imageName);
    }


    @Override
    public String moveProductImageToFinal(String tempUuid, String productUuid, String imagePathOrUrl) {
        String imagePath = extractPathFromUrl(imagePathOrUrl);
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }

        // For S3, update the path from temp UUID to product UUID
        // S3 paths: products/thumbnails/{uuid}/filename or products/galleries/{uuid}/filename
        String newPath = imagePath
                .replace(String.format("products/thumbnails/%s/", tempUuid),
                        String.format("products/thumbnails/%s/", productUuid))
                .replace(String.format("products/galleries/%s/", tempUuid),
                        String.format("products/galleries/%s/", productUuid));
        // Return the moved path (still a path, not a full URL - caller converts to URL)
        return newPath;
    }


    @Override
    public String moveVariantImageToFinal(String tempUuid, String productUuid, String variantUuid, String imagePathOrUrl) {
        String imagePath = extractPathFromUrl(imagePathOrUrl);
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }

        // For S3, update the path from temp UUID to variant UUID
        // S3 paths: variants/thumbnails/{uuid}/filename or variants/galleries/{uuid}/filename
        String newPath = imagePath
                .replace(String.format("variants/thumbnails/%s/", tempUuid),
                        String.format("variants/thumbnails/%s/", variantUuid))
                .replace(String.format("variants/galleries/%s/", tempUuid),
                        String.format("variants/galleries/%s/", variantUuid));
        // Return the moved path (still a path, not a full URL - caller converts to URL)
        return newPath;
    }

    @Override
    public String extractTempUuidFromPath(String imagePathOrUrl) {
        if (imagePathOrUrl == null || imagePathOrUrl.isEmpty()) {
            return null;
        }

        // Extract path from full URL if needed
        String imagePath = extractPathFromUrl(imagePathOrUrl);

        try {
            // Extract UUID from path pattern: products/thumbnails/{uuid}/filename or variants/thumbnails/{uuid}/filename
            String[] parts = imagePath.split("/");
            if (parts.length >= 3) {
                // parts[0] = products/variants, parts[1] = uuid, parts[2] = filename
                return parts[1];
            }
        } catch (Exception e) {
            log.warn("Could not extract temp UUID from path: {}", imagePathOrUrl);
        }

        return null;
    }


    @Override
    public boolean isTempImage(String imagePathOrUrl) {
        if (imagePathOrUrl == null || imagePathOrUrl.isEmpty()) {
            return false;
        }

        // Check if the path contains a temp UUID pattern
        // For S3, we consider it temp if it can be extracted
        String tempUuid = extractTempUuidFromPath(imagePathOrUrl);
        return tempUuid != null;
    }

    /**
     * Extract the S3 path from a full URL.
     * If already a plain path, return as-is.
     * If a full URL (https://domain/path), extract the path portion.
     */
    private String extractPathFromUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) {
            return pathOrUrl;
        }
        if (pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("http://")) {
            try {
                java.net.URI uri = new java.net.URI(pathOrUrl);
                String path = uri.getPath();
                return path != null && path.startsWith("/") ? path.substring(1) : path;
            } catch (Exception e) {
                log.warn("Could not parse URL '{}', using as-is", pathOrUrl);
                return pathOrUrl;
            }
        }
        return pathOrUrl;
    }

    @Override
    public ImageUploadResponse updateVariantThumbnail(MultipartFile file, String variantUuid, String oldImagePath) {
        log.info("Updating variant thumbnail for variant: {}", variantUuid);
        log.debug("Deleting old variant thumbnail: {}", oldImagePath);
        validateImage(file);

        // Delete old image if it exists
        if (oldImagePath != null && !oldImagePath.isEmpty()) {
            try {
                localStorageService.deleteFile(oldImagePath);
                log.info("Successfully deleted old variant thumbnail: {}", oldImagePath);
            } catch (Exception e) {
                log.warn("Failed to delete old variant thumbnail: {}, proceeding with upload anyway", oldImagePath, e);
            }
        }

        // Upload new image
        String folderPath = String.format("variants/thumbnails/%s", variantUuid);
        ImageUploadResponse response = localStorageService.uploadFile(file, folderPath, "variant-thumbnail");
        log.info("Successfully uploaded new variant thumbnail for variant: {}", variantUuid);
        return response;
    }


    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Please select an image to upload");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum limit of 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Invalid file type. Only JPEG, PNG, and WebP images are allowed");
        }
    }
}

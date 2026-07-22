package org.psint.beyosclothing.modules.products.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.LocalStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link LocalStorageService}.
 * <p>
 * Images are written under the configured upload directory (same directory the
 * {@code /uploads/**} resource handler serves) and their public URLs are built
 * from the configured base URL so the frontend can load them directly.
 */
@Service
@Slf4j
public class LocalStorageServiceImpl implements LocalStorageService {

    /** Root directory on disk where uploaded files are stored. */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** Base URL of this application, used to build absolute image URLs. */
    @Value("${app.upload.base-url:http://localhost:8080}")
    private String baseUrl;

    /** URL path that the {@code /uploads/**} static resource handler is mapped to. */
    private static final String PUBLIC_PATH = "uploads";

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    @Override
    public ImageUploadResponse uploadFile(MultipartFile file, String folderPath, String prefix) {
        validateImage(file);

        String extension = resolveExtension(file.getOriginalFilename());
        String uniqueFilename = String.format("%s-%s%s", prefix, UUID.randomUUID(), extension);
        String objectKey = String.format("%s/%s", folderPath, uniqueFilename);

        return storeFile(file, objectKey);
    }

    @Override
    public ImageUploadResponse uploadFileWithKey(MultipartFile file, String objectKey) {
        validateImage(file);
        return storeFile(file, objectKey);
    }

    private ImageUploadResponse storeFile(MultipartFile file, String objectKey) {
        Path targetPath = resolveSafePath(objectKey);

        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Stored image on local storage: {}", objectKey);

            return ImageUploadResponse.builder()
                    .imageName(objectKey)
                    .imageUrl(getPublicUrl(objectKey))
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Error storing image on local storage: {}", objectKey, e);
            throw new BadRequestException("Failed to store image: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileKey) {
        String objectKey = extractKey(fileKey);
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            Path targetPath = resolveSafePath(objectKey);
            boolean deleted = Files.deleteIfExists(targetPath);
            log.info("Local image delete for key '{}' -> {}", objectKey, deleted ? "deleted" : "not found");
        } catch (IOException e) {
            log.warn("Failed to delete local image: {}", objectKey, e);
        }
    }

    @Override
    public String getPublicUrl(String fileKey) {
        String objectKey = extractKey(fileKey);
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return String.format("%s/%s/%s", normalizedBase, PUBLIC_PATH, objectKey);
    }

    /**
     * Resolve an object key to an absolute path inside the upload directory,
     * rejecting any key that escapes the upload directory (path traversal).
     */
    private Path resolveSafePath(String objectKey) {
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(objectKey).normalize();

        if (!targetPath.startsWith(basePath)) {
            throw new BadRequestException("Invalid image path");
        }
        return targetPath;
    }

    /**
     * Normalize a stored value (a full public URL or a bare object key) down to
     * the object key relative to the upload directory.
     */
    private String extractKey(String fileKeyOrUrl) {
        if (fileKeyOrUrl == null || fileKeyOrUrl.isBlank()) {
            return fileKeyOrUrl;
        }

        String value = fileKeyOrUrl;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                String path = new URI(value).getPath();
                value = (path != null && path.startsWith("/")) ? path.substring(1) : path;
            } catch (Exception e) {
                log.warn("Could not parse image URL '{}', using as-is", fileKeyOrUrl);
                return fileKeyOrUrl;
            }
        }

        // Strip the "uploads/" public path prefix if the value already contains it
        String prefix = PUBLIC_PATH + "/";
        if (value != null && value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        return value;
    }

    private String resolveExtension(String originalFilename) {
        return (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
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

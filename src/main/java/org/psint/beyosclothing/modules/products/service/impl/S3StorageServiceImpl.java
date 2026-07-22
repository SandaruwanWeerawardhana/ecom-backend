package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.S3StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 Storage Service Implementation
 * Handles file upload/download operations with AWS S3
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class S3StorageServiceImpl implements S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.aws.s3.region:us-east-1}")
    private String region;

    @Value("${app.aws.s3.use-public-access:false}")
    private boolean usePublicAccess;

    @Value("${app.aws.s3.custom-domain:}")
    private String customImageDomain;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    @Override
    public ImageUploadResponse uploadFile(MultipartFile file, String folderPath, String prefix) {
        log.info("Uploading file to S3: bucket={}, folder={}, prefix={}", bucketName, folderPath, prefix);

        // Validate file
        validateImage(file);

        try {
            // Generate unique object key
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String uniqueFilename = String.format("%s-%s%s", prefix, UUID.randomUUID(), extension);

            // Build S3 object key (path in bucket)
            String objectKey = String.format("%s/%s", folderPath, uniqueFilename);

            // Prepare put object request
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize());

            // Set public-read ACL if public access is enabled
            if (usePublicAccess) {
                requestBuilder.acl(ObjectCannedACL.PUBLIC_READ);
            }

            PutObjectRequest putObjectRequest = requestBuilder.build();

            // Upload file to S3; the SDK does not close caller-provided streams
            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromInputStream(inputStream, file.getSize())
                );
            }

            log.info("File uploaded successfully to S3: {}", objectKey);

            // Build response URL
            // Priority: 1. Custom domain (if configured), 2. Public URL, 3. Presigned URL
            String fileUrl;
            if (customImageDomain != null && !customImageDomain.isEmpty()) {
                fileUrl = getPublicUrl(objectKey); // Will use custom domain
                log.debug("Using custom domain URL: {}", fileUrl);
            } else if (usePublicAccess) {
                fileUrl = getPublicUrl(objectKey); // Will use S3 public URL
                log.debug("Using S3 public URL: {}", fileUrl);
            } else {
                fileUrl = generatePresignedUrl(objectKey, 60); // 1 hour for private files
                log.debug("Using presigned URL (expires in 60 minutes)");
            }

            return ImageUploadResponse.builder()
                    .imageName(objectKey)
                    .imageUrl(fileUrl)
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new BadRequestException("Failed to upload file to S3: " + e.getMessage());
        } catch (S3Exception e) {
            log.error("S3 service error while uploading file", e);
            throw new BadRequestException("S3 service error: " + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public ImageUploadResponse uploadFileWithKey(MultipartFile file, String objectKey) {
        log.info("Uploading file to S3 with explicit key: bucket={}, objectKey={}", bucketName, objectKey);

        // Validate file
        validateImage(file);

        try {
            // Determine content type and length
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize());

            if (usePublicAccess) {
                requestBuilder.acl(ObjectCannedACL.PUBLIC_READ);
            }

            PutObjectRequest putObjectRequest = requestBuilder.build();

            // The SDK does not close caller-provided streams
            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromInputStream(inputStream, file.getSize())
                );
            }

            // Build URL
            String fileUrl = (customImageDomain != null && !customImageDomain.isEmpty())
                    ? getPublicUrl(objectKey)
                    : (usePublicAccess ? getPublicUrl(objectKey) : generatePresignedUrl(objectKey, 60));

            return ImageUploadResponse.builder()
                    .imageName(objectKey)
                    .imageUrl(fileUrl)
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Error uploading file to S3 with key", e);
            throw new BadRequestException("Failed to upload file to S3: " + e.getMessage());
        } catch (S3Exception e) {
            log.error("S3 service error while uploading file with key", e);
            throw new BadRequestException("S3 service error: " + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public void deleteFile(String fileKeyOrUrl) {
        String fileKey = extractS3Key(fileKeyOrUrl);
        log.info("Deleting file from S3: bucket={}, key={}", bucketName, fileKey);

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted successfully from S3: {}", fileKey);

        } catch (S3Exception e) {
            log.error("Error deleting file from S3: {}", fileKey, e);
            throw new BadRequestException("Failed to delete file from S3: " + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public String getPublicUrl(String fileKeyOrUrl) {
        String fileKey = extractS3Key(fileKeyOrUrl);
        // Use custom domain if configured, otherwise use AWS S3 URL
        if (customImageDomain != null && !customImageDomain.isEmpty()) {
            return String.format("https://%s/%s", customImageDomain, fileKey);
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileKey);
    }

    @Override
    public String generatePresignedUrl(String fileKeyOrUrl, int expirationMinutes) {
        String fileKey = extractS3Key(fileKeyOrUrl);
        log.debug("Generating presigned URL for file: {}, expiration: {} minutes", fileKey, expirationMinutes);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            String url = presignedRequest.url().toString();
            log.debug("Generated presigned URL: {}", url);

            return url;

        } catch (S3Exception e) {
            log.error("Error generating presigned URL for file: {}", fileKey, e);
            throw new BadRequestException("Failed to generate presigned URL: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Validate uploaded image
     */
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


    private String extractS3Key(String fileKeyOrUrl) {
        if (fileKeyOrUrl == null || fileKeyOrUrl.isBlank()) {
            return fileKeyOrUrl;
        }
        if (fileKeyOrUrl.startsWith("https://") || fileKeyOrUrl.startsWith("http://")) {
            try {
                java.net.URI uri = new java.net.URI(fileKeyOrUrl);
                String path = uri.getPath();
                // Remove leading slash
                return path != null && path.startsWith("/") ? path.substring(1) : path;
            } catch (Exception e) {
                log.warn("Could not parse URL '{}', using as-is", fileKeyOrUrl);
                return fileKeyOrUrl;
            }
        }
        return fileKeyOrUrl;
    }
}

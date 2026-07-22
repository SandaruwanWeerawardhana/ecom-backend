package org.psint.beyosclothing.modules.products.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Image Upload Controller
 * Handles image uploads for products and variants
 * Images are uploaded BEFORE creating/updating products
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/products/images")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Images", description = "APIs for uploading product and variant images")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductImageController {

    private final ImageStorageService imageStorageService;

    /**
     * Upload product thumbnail
     * Required: ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission
     */
    @PostMapping(value = "/product/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and (hasAuthority('CREATE_PRODUCT') or hasAuthority('EDIT_PRODUCT'))")
    @Operation(
            summary = "Upload product thumbnail",
            description = "Upload a thumbnail image for a product. Returns the image name to be used in product creation/update. " +
                    "Requires ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ImageUploadResponse>> uploadProductThumbnail(
            @RequestParam("file") MultipartFile file) {
        log.info("POST /api/v1/products/images/product/thumbnail - Uploading product thumbnail");

        // Generate temporary UUID for folder (will be replaced with actual product UUID)
        String tempUuid = UUID.randomUUID().toString();
        ImageUploadResponse response = imageStorageService.uploadProductThumbnail(file, tempUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<ImageUploadResponse>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Product thumbnail uploaded successfully")
                        .data(response)
                        .build());
    }

    /**
     * Upload product gallery images
     * Required: ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission
     */
    @PostMapping(value = "/product/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and (hasAuthority('CREATE_PRODUCT') or hasAuthority('EDIT_PRODUCT'))")
    @Operation(
            summary = "Upload product gallery images",
            description = "Upload multiple gallery images for a product. Returns image names to be used in product creation/update. " +
                    "Requires ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<List<ImageUploadResponse>>> uploadProductGalleryImages(
            @RequestParam("files") List<MultipartFile> files) {
        log.info("POST /api/v1/products/images/product/gallery - Uploading {} product gallery images", files.size());

        // Generate temporary UUID for folder
        String tempUuid = UUID.randomUUID().toString();
        List<ImageUploadResponse> responses = imageStorageService.uploadProductGalleryImages(files, tempUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<List<ImageUploadResponse>>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Product gallery images uploaded successfully")
                        .data(responses)
                        .build());
    }

    /**
     * Upload variant thumbnail
     * Required: ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission
     */
    @PostMapping(value = "/variant/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and (hasAuthority('CREATE_PRODUCT') or hasAuthority('EDIT_PRODUCT'))")
    @Operation(
            summary = "Upload variant thumbnail",
            description = "Upload a thumbnail image for a product variant. Returns the image name to be used in variant creation. " +
                    "Requires ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ImageUploadResponse>> uploadVariantThumbnail(
            @RequestParam("file") MultipartFile file) {
        log.info("POST /api/v1/products/images/variant/thumbnail - Uploading variant thumbnail");

        // Generate temporary UUID for folder
        String tempUuid = UUID.randomUUID().toString();
        ImageUploadResponse response = imageStorageService.uploadVariantThumbnail(file, tempUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<ImageUploadResponse>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Variant thumbnail uploaded successfully")
                        .data(response)
                        .build());
    }

    /**
     * Upload variant gallery images
     * Required: ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission
     */
    @PostMapping(value = "/variant/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and (hasAuthority('CREATE_PRODUCT') or hasAuthority('EDIT_PRODUCT'))")
    @Operation(
            summary = "Upload variant gallery images",
            description = "Upload multiple gallery images for a product variant. Returns image names to be used in variant creation. " +
                    "Requires ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<List<ImageUploadResponse>>> uploadVariantGalleryImages(
            @RequestParam("files") List<MultipartFile> files) {
        log.info("POST /api/v1/products/images/variant/gallery - Uploading {} variant gallery images", files.size());

        // Generate temporary UUID for folder
        String tempUuid = UUID.randomUUID().toString();
        List<ImageUploadResponse> responses = imageStorageService.uploadVariantGalleryImages(files, tempUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<List<ImageUploadResponse>>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Variant gallery images uploaded successfully")
                        .data(responses)
                        .build());
    }

    /**
     * Upload product category image
     * Required: ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission
     */
    @PostMapping(value = "/category", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    @PreAuthorize("hasAuthority('ROLE_ADMIN') and (hasAuthority('CREATE_PRODUCT') or hasAuthority('EDIT_PRODUCT'))")
    @Operation(
            summary = "Upload product category image",
            description = "Upload an image for a product category. Returns the image name to be used in category creation/update. " +
                    "Requires ADMIN user type with CREATE_PRODUCT or EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ImageUploadResponse>> uploadProductCategory(
            @RequestParam("file") MultipartFile file) {
        log.info("POST /api/v1/products/images/category - Uploading product category image");

        // Generate temporary UUID for folder
        String tempUuid = UUID.randomUUID().toString();
        ImageUploadResponse response = imageStorageService.uploadProductCategory(file, tempUuid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<ImageUploadResponse>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Product category image uploaded successfully")
                        .data(response)
                        .build());
    }


    @PutMapping(value = "/variant/{variantUuid}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Update variant thumbnail",
            description = "Update the thumbnail image for an existing product variant. Deletes the old image and uploads the new one. " +
                    "Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ImageUploadResponse>> updateVariantThumbnail(
            @PathVariable String variantUuid,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "oldImagePath", required = false) String oldImagePath) {
        log.info("PUT /api/v1/products/images/variant/{}/thumbnail - Updating variant thumbnail", variantUuid);

        ImageUploadResponse response = imageStorageService.updateVariantThumbnail(file, variantUuid, oldImagePath);

        return ResponseEntity.ok()
                .body(APIResponse.<ImageUploadResponse>builder()
                        .responseCode(0)
                        .success(true)
                        .message("Variant thumbnail updated successfully")
                        .data(response)
                        .build());
    }

}

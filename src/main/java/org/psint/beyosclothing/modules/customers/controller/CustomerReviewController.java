package org.psint.beyosclothing.modules.customers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.customers.dto.request.CreateReviewRequest;
import org.psint.beyosclothing.modules.customers.dto.response.ReviewResponse;
import org.psint.beyosclothing.modules.customers.service.CustomerReviewService;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.ImageStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Customer Review Controller
 * Manages product reviews submitted by customers
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/reviews")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Reviews", description = "APIs for managing customer product reviews")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerReviewController {

    private final CustomerReviewService reviewService;
    private final ImageStorageService imageStorageService;

    /**
     * Upload review images (before submitting review)
     * Customer endpoint - requires CUSTOMER authentication
     */
    @PostMapping("/upload-images")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @Operation(
            summary = "Upload review images",
            description = "Upload images for a review before submitting. Use a temporary UUID for the upload, then include the returned image URLs in the review submission. Requires CUSTOMER role."
    )
    public ResponseEntity<APIResponse<List<ImageUploadResponse>>> uploadReviewImages(
            @RequestParam("images") List<MultipartFile> images) {
        log.info("POST /api/v1/reviews/upload-images - Uploading {} review images", images.size());

        // Generate temporary UUID for upload
        String tempUuid = UUID.randomUUID().toString();

        List<ImageUploadResponse> responses = imageStorageService.uploadReviewImages(images, tempUuid);

        return ResponseEntity.ok(APIResponse.<List<ImageUploadResponse>>builder()
                .success(true)
                .message("Review images uploaded successfully")
                .data(responses)
                .build());
    }

    /**
     * Create a product review
     * Customer endpoint - requires CUSTOMER authentication
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @Operation(
            summary = "Create product review",
            description = "Submit a product review with rating and optional comment and images. " +
                    "Reviews must be approved by admin before appearing publicly. Requires CUSTOMER role."
    )
    public ResponseEntity<APIResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            Authentication authentication) {
        log.info("POST /api/v1/reviews - Creating review for product: {}", request.getProductUuid());

        // Extract email from authentication and find customer
        String email = extractEmailFromAuth(authentication);
        
        // Pass email to service - service will handle finding customer by email
        ReviewResponse response = reviewService.createReviewByEmail(request, email);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<ReviewResponse>builder()
                        .success(true)
                        .message("Review submitted successfully. It will be visible after admin approval.")
                        .data(response)
                        .build());
    }

    /**
     * Get all reviews (Admin only)
     * Required: ADMIN user type
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get all reviews (Admin)",
            description = "Retrieve all product reviews with pagination. Admin only."
    )
    public ResponseEntity<APIResponse<PageResponse<ReviewResponse>>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/reviews/admin/all - Fetching all reviews");

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ReviewResponse> response = reviewService.getAllReviews(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ReviewResponse>>builder()
                .success(true)
                .message("Reviews retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get pending reviews (Admin only)
     * Required: ADMIN user type
     */
    @GetMapping("/admin/pending")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Get pending reviews (Admin)",
            description = "Retrieve all pending (unapproved) reviews. Admin only."
    )
    public ResponseEntity<APIResponse<PageResponse<ReviewResponse>>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/reviews/admin/pending - Fetching pending reviews");

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ReviewResponse> response = reviewService.getReviewsByApprovalStatus(false, pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ReviewResponse>>builder()
                .success(true)
                .message("Pending reviews retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get approved reviews for a product (Public)
     * Public endpoint - no authentication required
     */
    @GetMapping("/product/{productUuid}")
    @Operation(
            summary = "Get product reviews",
            description = "Retrieve all approved reviews for a specific product. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable String productUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/reviews/product/{} - Fetching approved reviews", productUuid);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ReviewResponse> response = reviewService.getProductReviews(productUuid, true, pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ReviewResponse>>builder()
                .success(true)
                .message("Product reviews retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Approve a review (Admin only)
     * Required: ADMIN user type
     */
    @PatchMapping("/admin/{reviewUuid}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Approve review (Admin)",
            description = "Approve a customer review to make it visible publicly. Admin only."
    )
    public ResponseEntity<APIResponse<ReviewResponse>> approveReview(@PathVariable String reviewUuid) {
        log.info("PATCH /api/v1/reviews/admin/{}/approve - Approving review", reviewUuid);

        ReviewResponse response = reviewService.approveReview(reviewUuid);

        return ResponseEntity.ok(APIResponse.<ReviewResponse>builder()
                .success(true)
                .message("Review approved successfully")
                .data(response)
                .build());
    }

    /**
     * Reject a review (Admin only)
     * Required: ADMIN user type
     */
    @PatchMapping("/admin/{reviewUuid}/reject")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Reject review (Admin)",
            description = "Reject/Unapprove a customer review. Admin only."
    )
    public ResponseEntity<APIResponse<ReviewResponse>> rejectReview(@PathVariable String reviewUuid) {
        log.info("PATCH /api/v1/reviews/admin/{}/reject - Rejecting review", reviewUuid);

        ReviewResponse response = reviewService.rejectReview(reviewUuid);

        return ResponseEntity.ok(APIResponse.<ReviewResponse>builder()
                .success(true)
                .message("Review rejected successfully")
                .data(response)
                .build());
    }

    /**
     * Get customer's own reviews
     * Customer endpoint - requires CUSTOMER authentication
     */
    @GetMapping("/my-reviews")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @Operation(
            summary = "Get my reviews",
            description = "Retrieve all reviews submitted by the authenticated customer. Requires CUSTOMER role."
    )
    public ResponseEntity<APIResponse<PageResponse<ReviewResponse>>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            Authentication authentication) {
        log.info("GET /api/v1/reviews/my-reviews - Fetching customer's reviews");

        // Extract email from authentication principal
        String email = extractEmailFromAuth(authentication);
        log.debug("Extracted email from authentication: {}", email);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ReviewResponse> response = reviewService.getCustomerReviewsByEmail(email, pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ReviewResponse>>builder()
                .success(true)
                .message("Your reviews retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Helper method to extract email from authentication
     * @param authentication Spring Security Authentication object
     * @return email address of the authenticated user
     */
    private String extractEmailFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication not found");
        }

        Object principal = authentication.getPrincipal();
        String email;

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            log.debug("Extracted email from UserDetails principal: {}", email);
        } else if (principal instanceof String) {
            email = (String) principal;
            log.debug("Extracted email from String principal: {}", email);
        } else {
            throw new IllegalStateException("Unable to extract email from authentication principal");
        }

        return email;
    }
}

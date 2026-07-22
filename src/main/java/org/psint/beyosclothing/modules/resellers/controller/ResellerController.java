package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.resellers.dto.request.PasswordChangeRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerRegistrationRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerUpdateRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerImageUploadResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProfileResponse;
import org.psint.beyosclothing.modules.resellers.exception.InvalidCurrentPasswordException;
import org.psint.beyosclothing.modules.resellers.exception.PasswordReuseException;
import org.psint.beyosclothing.modules.resellers.exception.TooManyAttemptsException;
import org.psint.beyosclothing.modules.resellers.exception.WeakPasswordException;
import org.psint.beyosclothing.modules.resellers.service.ResellerImageService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST Controller for Reseller Registration and Profile Management
 */
@RestController
@RequestMapping("/api/v1/resellers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Management", description = "Reseller registration and profile management endpoints")
public class ResellerController {

    private final ResellerService resellerService;
    private final JwtUtil jwtUtil;
    private final ResellerImageService resellerImageService;

    /**
     * Register new reseller (Public endpoint - no authentication required)
     */
    @PostMapping("/register")
    @Operation(summary = "Register as a reseller",
               description = "Public endpoint for reseller registration. Creates user account and reseller profile with PENDING status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Reseller registered successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProfileResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error or invalid input"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<ResellerProfileResponse> register(@Valid @RequestBody ResellerRegistrationRequest request) {
        log.info("Reseller registration request received for email: {}", request.getEmail());

        try {
            ResellerProfileResponse response = resellerService.register(request);
            log.info("Reseller registered successfully: {}", response.getUuid());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("already registered")) {
                log.warn("Registration failed - email already exists: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            throw e;
        }
    }

    /**
     * Get reseller profile (Authenticated endpoint - RESELLER role required)
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get reseller profile",
               description = "Returns the authenticated reseller's profile information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProfileResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
        @ApiResponse(responseCode = "404", description = "Reseller profile not found")
    })
    public ResponseEntity<ResellerProfileResponse> getProfile(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        log.info("Fetching profile for reseller user ID: {}", userId);

        ResellerProfileResponse response = resellerService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update reseller profile (Authenticated endpoint - RESELLER role required)
     */
    @PutMapping("/profile")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Update reseller profile",
               description = "Updates allowed profile fields (name, address, phone)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProfileResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<ResellerProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ResellerUpdateRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        log.info("Updating profile for reseller user ID: {}", userId);

        ResellerProfileResponse response = resellerService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Upload reseller profile image (Authenticated endpoint - RESELLER role required)
     */
    @PostMapping(value = "/profile/image", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Upload reseller profile image",
            description = "Uploads a reseller profile image to AWS S3 and saves the resulting imageUrl on the reseller profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image uploaded successfully",
                    content = @Content(schema = @Schema(implementation = ResellerImageUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResellerImageUploadResponse> uploadProfileImage(
            Authentication authentication,
            @RequestParam("image") MultipartFile image) {
        Long userId = jwtUtil.getUserId(authentication);
        log.info("Uploading profile image for reseller userId={}", userId);

        ResellerImageUploadResponse response = resellerImageService.uploadProfileImage(userId, image);
        return ResponseEntity.ok(response);
    }

    /**
     * Change password for authenticated reseller
     */
    @PatchMapping("/password/{uuId}")
//    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Change password",
               description = "Change password for the authenticated reseller. Provide currentPassword and newPassword.")
    public ResponseEntity<?> changePassword(@PathVariable String uuId,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        try {
            resellerService.changePassword(uuId, request.getCurrentPassword(), request.getNewPassword(), request.getConfirmPassword());
            return ResponseEntity.ok(Map.of("success", true, "message", "Password updated"));
        } catch (PasswordReuseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", "New password must be different from current password"));
        } catch (WeakPasswordException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Password validation failed"));
        } catch (InvalidCurrentPasswordException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Unable to update password"));
        } catch (TooManyAttemptsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("success", false, "message", "Too many password attempts. Try again later"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Forbidden"));
        } catch (Exception e) {
            log.error("Unexpected error changing password for uuId={}", uuId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Unable to update password"));
        }

    }

    /**
     * Get reseller status (Public endpoint - can be accessed with or without authentication)
     * If authenticated, returns status for the authenticated user.
     * If not authenticated, requires email parameter.
     */
    @GetMapping("/status")
    @Operation(summary = "Get reseller status",
               description = "Returns current approval status with relevant messages. Requires email parameter if not authenticated.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Bad request - missing email parameter"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<StatusResponse> getStatus(
            Authentication authentication,
            @RequestParam(required = false) String email) {

        // If authenticated, use authentication, otherwise use email parameter
        String statusMessage;
        ResellerProfileResponse profile;

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Long userId = jwtUtil.getUserId(authentication);
            log.info("Fetching status for authenticated reseller user ID: {}", userId);
            statusMessage = resellerService.getStatus(userId);
            profile = resellerService.getProfile(userId);
        } else {
            if (email == null || email.isBlank()) {
                log.warn("Status request without authentication and without email parameter");
                return ResponseEntity.badRequest().build();
            }
            log.info("Fetching status for reseller email (public): {}", email);
            statusMessage = resellerService.getStatusByEmail(email);
            profile = resellerService.getProfileByEmail(email);
        }

        StatusResponse response = StatusResponse.builder()
                .status(profile.getStatus())
                .statusMessage(statusMessage)
                .registrationDate(profile.getRegistrationDate())
                .approvalDate(profile.getApprovalDate())
                .isActive(profile.getIsActive())
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') ")
    @Operation(
            summary = "Delete Reseller"
    )
    public ResponseEntity<APIResponse<Void>> deleteProduct(@PathVariable String uuid) {
        log.info("DELETE /api/v1/Reseller/{} - Deleting Reseller", uuid);

        resellerService.deleteReseller(uuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Reseller deleted successfully")
                .build());
    }


    /**
     * Status Response DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "Reseller status response")
    public static class StatusResponse {
        @Schema(description = "Current status", example = "PENDING")
        private String status;

        @Schema(description = "Status message", example = "Your application is under review")
        private String statusMessage;

        @Schema(description = "Registration date")
        private java.time.LocalDateTime registrationDate;

        @Schema(description = "Approval date (if approved)")
        private java.time.LocalDateTime approvalDate;

        @Schema(description = "Is account active", example = "true")
        private Boolean isActive;
    }
}

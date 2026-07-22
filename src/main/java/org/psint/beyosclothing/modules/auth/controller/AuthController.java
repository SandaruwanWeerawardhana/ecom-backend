package org.psint.beyosclothing.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.psint.beyosclothing.common.dto.APIResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.dto.request.email.EmailVerificationRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.DevPasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetConfirmRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.token.RefreshTokenRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.LoginRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.RegistrationRequest;
import org.psint.beyosclothing.modules.auth.dto.response.user.AuthenticationResponse;
import org.psint.beyosclothing.modules.auth.dto.response.user.CurrentUserResponse;
import org.psint.beyosclothing.modules.auth.dto.response.user.LoginResponse;
import org.psint.beyosclothing.modules.auth.service.impl.AuthServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles all authentication endpoints with cookie-based session management
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    private final AuthServiceImpl authService;

    @Operation(
        summary = "Register new user",
        description = "Register a new user with email and password. Sends verification email after successful registration."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<APIResponse<AuthenticationResponse>> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/v1/auth/register - Role: {}", request.getRole());

        AuthenticationResponse response = authService.register(request, httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<AuthenticationResponse>builder()
                        .success(true)
                        .message("Registration successful. Please verify your email.")
                        .data(response)
                        .build());
    }

    @Operation(
        summary = "User login (Bearer Token)",
        description = "Authenticate user with email and password. Returns JWT access and refresh tokens in JSON body."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "403", description = "Account locked or not verified")
    })
    @PostMapping("/login")
    public ResponseEntity<APIResponse<AuthenticationResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/v1/auth/login - Email: {}", request.getEmail());

        AuthenticationResponse response = authService.login(request, httpRequest);

        return ResponseEntity.ok(APIResponse.<AuthenticationResponse>builder()
                .success(true)
                .message("Login successful")
                .data(response)
                .build());
    }

    @Operation(
        summary = "User login (Cookie-based)",
        description = "Authenticate user and set HTTP-only cookies. Tokens are NOT returned in response body. " +
                     "Use this endpoint for browser-based authentication with automatic cookie management."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful, cookies set",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login/cookie")
    public ResponseEntity<APIResponse<LoginResponse>> loginWithCookie(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("POST /api/v1/auth/login/cookie - Email: {}", request.getEmail());

        LoginResponse response = authService.loginWithCookies(request, httpRequest, httpResponse);

        return ResponseEntity.ok(APIResponse.<LoginResponse>builder()
                .success(true)
                .message("Login successful. Cookies set.")
                .data(response)
                .build());
    }

    @Operation(
        summary = "Get current authenticated user",
        description = "Returns information about the currently authenticated user. " +
                     "Supports both Bearer token and cookie-based authentication.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved",
            content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token")
    })
    @GetMapping("/me")
    public ResponseEntity<APIResponse<CurrentUserResponse>> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        log.info("GET /api/v1/auth/me - Email: {}", email);

        CurrentUserResponse response = authService.getCurrentUser(email);

        return ResponseEntity.ok(APIResponse.<CurrentUserResponse>builder()
                .success(true)
                .message("User information retrieved")
                .data(response)
                .build());
    }

    @Operation(
        summary = "User logout (Bearer Token)",
        description = "Invalidate current access token and end user session. Requires valid JWT token.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token")
    })
    @PostMapping("/logout")
    public ResponseEntity<APIResponse<Void>> logout(
            Authentication authentication,
            @Parameter(description = "JWT token in format: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7); // Remove "Bearer " prefix
        String username = authentication.getName();

        log.info("POST /api/v1/auth/logout - Username: {}", username);

        authService.logout(username, token);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Logout successful")
                .build());
    }

    @Operation(
        summary = "User logout (Cookie-based)",
        description = "Invalidate session and clear HTTP-only cookies.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful, cookies cleared"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout/cookie")
    public ResponseEntity<APIResponse<Void>> logoutWithCookie(
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletResponse httpResponse) {

        String username = authentication.getName();
        log.info("POST /api/v1/auth/logout/cookie - Username: {}", username);

        // Extract token from header if present, otherwise filter already validated cookie
        String token = "";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        authService.logoutWithCookies(username, token, httpResponse);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Logout successful. Cookies cleared.")
                .build());
    }

    @Operation(
        summary = "Refresh access token",
        description = "Get a new access token using a valid refresh token. The refresh token remains valid."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<APIResponse<AuthenticationResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/v1/auth/refresh-token");

        AuthenticationResponse response = authService.refreshToken(request);

        return ResponseEntity.ok(APIResponse.<AuthenticationResponse>builder()
                .success(true)
                .message("Token refreshed successfully")
                .data(response)
                .build());
    }

    @Operation(
        summary = "Request password reset",
        description = "Send password reset email with verification code to user's registered email address."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset email sent"),
        @ApiResponse(responseCode = "404", description = "Email not found")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<APIResponse<Void>> forgotPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/v1/auth/forgot-password - Email: {}", request.getEmail());

        authService.requestPasswordReset(request, httpRequest);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Password reset instructions sent to your email")
                .build());
    }

    @Operation(
        summary = "Reset password with code",
        description = "Reset password using the verification code sent to email."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successful"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired reset code")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<APIResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        log.info("POST /api/v1/auth/reset-password");

        authService.confirmPasswordReset(request);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Password reset successful")
                .build());
    }

    @Operation(
        summary = "Verify email address",
        description = "Verify user's email address using the verification code sent during registration."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired verification code")
    })
    @PostMapping("/verify-email")
    public ResponseEntity<APIResponse<Void>> verifyEmail(
            @Valid @RequestBody EmailVerificationRequest request) {
        log.info("POST /api/v1/auth/verify-email");

        authService.verifyEmail(request);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Email verified successfully")
                .build());
    }

    @Operation(
        summary = "Resend verification email",
        description = "Request a new verification email to be sent to the specified email address."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification email sent"),
        @ApiResponse(responseCode = "400", description = "Email already verified or not found")
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<APIResponse<Void>> resendVerification(
            @Parameter(description = "User's email address", required = true)
            @RequestParam String email) {
        log.info("POST /api/v1/auth/resend-verification - Email: {}", email);

        authService.resendVerificationEmail(email);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Verification email sent")
                .build());
    }

    @Operation(
        summary = "🔥 DEV ONLY: Reset password without validation",
        description = "⚠️ DEVELOPMENT ONLY - Reset password without any token validation or security checks. " +
                     "This endpoint MUST be disabled in production!"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successful"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/dev/reset-password")
    public ResponseEntity<APIResponse<Void>> devResetPassword(
            @RequestBody DevPasswordResetRequest request) {
        log.warn("⚠️ POST /api/v1/auth/dev/reset-password - Email: {}", request.getEmail());

        authService.devResetPassword(request);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("⚠️ DEV MODE: Password reset successful (NO VALIDATION)")
                .build());
    }

    @Operation(
        summary = "Health check",
        description = "Check if authentication service is running and healthy."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping("/health")
    public ResponseEntity<APIResponse<String>> health() {
        return ResponseEntity.ok(APIResponse.<String>builder()
                .success(true)
                .message("Auth service is running")
                .data("OK")
                .build());
    }
}

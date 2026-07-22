package org.psint.beyosclothing.modules.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.psint.beyosclothing.modules.auth.dto.request.email.EmailVerificationRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.DevPasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetConfirmRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.token.RefreshTokenRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.LoginRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.RegistrationRequest;
import org.psint.beyosclothing.modules.auth.dto.response.user.AuthenticationResponse;

/**
 * Auth Service Interface
 * Defines all authentication operations
 */
public interface AuthService {

    AuthenticationResponse register(RegistrationRequest request, HttpServletRequest httpRequest);

    AuthenticationResponse login(LoginRequest request, HttpServletRequest httpRequest);

    void logout(String username, String token);

    void logoutWithCookies(String username, String token, HttpServletResponse httpResponse);

    AuthenticationResponse refreshToken(RefreshTokenRequest request);

    void requestPasswordReset(PasswordResetRequest request, HttpServletRequest httpRequest);

    void confirmPasswordReset(PasswordResetConfirmRequest request);

    void verifyEmail(EmailVerificationRequest request);

    void resendVerificationEmail(String email);

    /**
     * Development-only: Reset password without any validations
     * WARNING: This should NEVER be enabled in production
     */
    void devResetPassword(DevPasswordResetRequest request);
}

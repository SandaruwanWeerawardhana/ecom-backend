package org.psint.beyosclothing.modules.auth.service;

import org.psint.beyosclothing.modules.auth.events.EmailVerificationEvent;
import org.psint.beyosclothing.modules.auth.events.PasswordResetEvent;

/**
 * Email Service Interface
 * Handles all email operations
 */
public interface EmailService {

    /**
     * Send email verification email
     */
    void sendEmailVerification(EmailVerificationEvent event);

    /**
     * Send password reset email
     */
    void sendPasswordResetEmail(PasswordResetEvent event);

    /**
     * Send welcome email after registration
     */
    void sendWelcomeEmail(String email, String username);

    /**
     * Send security alert email
     */
    void sendSecurityAlert(String email, String username, String alertMessage);
}


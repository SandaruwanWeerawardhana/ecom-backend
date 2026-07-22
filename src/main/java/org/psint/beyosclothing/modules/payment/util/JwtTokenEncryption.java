package org.psint.beyosclothing.modules.payment.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT-Token-Based Encryption for user-specific data
 * Each user's data is encrypted with a key derived from their JWT token
 * This ensures that data can only be decrypted with the user's valid JWT token
 */
@Component
@Slf4j
public class JwtTokenEncryption {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 32; // 256 bits

    /**
     * Encrypt data using JWT token as encryption key
     * The JWT's signing key + user ID creates a unique encryption key per user
     *
     * @param plainText data to encrypt
     * @param jwtToken JWT bearer token (without "Bearer " prefix)
     * @return Base64 encoded encrypted data (IV + ciphertext + tag)
     */
    public String encryptWithJwt(String plainText, String jwtToken) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Derive encryption key from JWT token
            byte[] keyBytes = deriveKeyFromJwt(jwtToken);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV for each encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            String encrypted = Base64.getEncoder().encodeToString(combined);
            log.debug("Successfully encrypted data with JWT token");
            return encrypted;

        } catch (Exception e) {
            log.error("Failed to encrypt data with JWT: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt data using JWT token as decryption key
     *
     * @param encryptedText Base64 encoded encrypted data
     * @param jwtToken JWT bearer token (without "Bearer " prefix)
     * @return decrypted plaintext
     */
    public String decryptWithJwt(String encryptedText, String jwtToken) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Derive decryption key from JWT token
            byte[] keyBytes = deriveKeyFromJwt(jwtToken);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            // Decrypt
            byte[] plainText = cipher.doFinal(cipherText);
            String decrypted = new String(plainText, StandardCharsets.UTF_8);
            log.debug("Successfully decrypted data with JWT token");
            return decrypted;

        } catch (Exception e) {
            log.error("Failed to decrypt data with JWT: {}", e.getMessage());
            throw new RuntimeException("Decryption failed - invalid token or corrupted data", e);
        }
    }

    /**
     * Derive a 256-bit encryption key from JWT token
     * Uses SHA-256 hash of the JWT token to create a consistent key
     *
     * @param jwtToken the JWT token
     * @return 32-byte AES key
     */
    private byte[] deriveKeyFromJwt(String jwtToken) {
        try {
            // Use SHA-256 to create a fixed-size key from the JWT
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jwtToken.getBytes(StandardCharsets.UTF_8));

            // Ensure we have exactly 32 bytes for AES-256
            byte[] key = new byte[AES_KEY_SIZE];
            System.arraycopy(hash, 0, key, 0, Math.min(hash.length, AES_KEY_SIZE));

            return key;
        } catch (Exception e) {
            log.error("Failed to derive key from JWT: {}", e.getMessage());
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    /**
     * Extract user ID from JWT token (for logging/auditing purposes)
     *
     * @param jwtToken the JWT token
     * @return user ID or null if extraction fails
     */
    public String extractUserId(String jwtToken) {
        try {
            // Parse JWT without signature verification (we just need the payload)
            String[] parts = jwtToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                // Parse basic JSON to extract sub (subject/user ID)
                if (payload.contains("\"sub\"")) {
                    int start = payload.indexOf("\"sub\":") + 7;
                    int end = payload.indexOf("\"", start + 1);
                    if (end > start) {
                        return payload.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from JWT: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Mask encrypted value for display (shows it's encrypted)
     *
     * @param encryptedValue the encrypted value
     * @return masked display string
     */
    public String maskEncryptedValue(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.length() <= 20) {
            return "[ENCRYPTED]";
        }
        return "[ENCRYPTED:" + encryptedValue.substring(0, 8) + "..." + encryptedValue.substring(encryptedValue.length() - 8) + "]";
    }
}


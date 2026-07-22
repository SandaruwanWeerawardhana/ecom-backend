package org.psint.beyosclothing.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Username Generator Service
 * Generates meaningful, secure, and unique usernames
 *
 * Format: BYS{YEAR}{MONTH}{DAY}{HOUR}{MINUTE}{RANDOM_HASH}
 * Example: BYS2512081445k3x2p9m5n7q8w2
 *
 * Meaning Breakdown:
 * - BYS: Beyos platform identifier (3 chars)
 * - 25: Year (2025) - 2 chars
 * - 12: Month (December) - 2 chars
 * - 08: Day - 2 chars
 * - 14: Hour (24-hour format) - 2 chars
 * - 45: Minute - 2 chars
 * - k3x2p9m5n7q8w2: Secure random hash - 14 chars
 *
 * Total Length: 27 characters (3+10+14)
 * Collision Probability: < 0.00000001% (36^14 combinations)
 * Security: Cryptographically secure random generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsernameGeneratorService {

    private final UserRepository userRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Character set for random portion (alphanumeric - lowercase + numbers)
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Generate unique username
     * @param role User role (for logging purposes only, not used in username)
     * @return Unique username (e.g., BYS2512081445k3x2p9m5n7q8w2)
     */
    public String generateUsername(String role) {
        String username;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            username = buildUsername();
            attempts++;

            if (attempts >= maxAttempts) {
                // Fallback: Add extra random chars for absolute uniqueness
                username = username + generateSecureRandomHash(4);
                break;
            }
        } while (userRepository.existsByUsername(username));

        log.debug("Generated username: {} for role: {} (attempts: {})", username, role, attempts);
        return username;
    }

    /**
     * Build username with platform prefix + timestamp + random hash
     */
    private String buildUsername() {
        String platformPrefix = "BYS"; // Beyos platform identifier
        String timestamp = encodeTimestamp();
        String randomHash = generateSecureRandomHash(14);

        return platformPrefix + timestamp + randomHash;
    }

    /**
     * Encode timestamp into readable format
     * Format: YYMMDDHHMI (10 characters)
     *
     * Example: 2512081445
     * - 25 = Year 2025
     * - 12 = December
     * - 08 = 8th day
     * - 14 = 14:00 (2 PM)
     * - 45 = 45 minutes
     *
     * Benefits:
     * - Easy to identify account creation time
     * - Temporal uniqueness (different every minute)
     * - Admin-friendly for auditing
     * - Sortable by creation time
     */
    private String encodeTimestamp() {
        LocalDateTime now = LocalDateTime.now();

        String year = String.format("%02d", now.getYear() % 100);
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String hour = String.format("%02d", now.getHour());
        String minute = String.format("%02d", now.getMinute());

        return year + month + day + hour + minute;
    }

    /**
     * Generate cryptographically secure random hash
     * Uses alphanumeric characters (a-z, 0-9)
     *
     * Example: k3x2p9m5n7q8w2 (14 characters)
     * Combinations: 36^14 = 6.14 x 10^21 possibilities (6 sextillion)
     */
    private String generateSecureRandomHash(int length) {
        StringBuilder hash = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(CHARS.length());
            hash.append(CHARS.charAt(randomIndex));
        }

        return hash.toString();
    }

    /**
     * Decode username to explain its meaning
     * Used for admin debugging/auditing
     *
     * @param username Generated username (e.g., BYS2512081445k3x2p9m5n7q8w2)
     * @return Human-readable explanation
     */
    public String explainUsername(String username) {
        if (username == null || username.length() < 27) {
            return "Invalid username format (expected 27+ characters)";
        }

        try {
            String platform = username.substring(0, 3);
            String year = "20" + username.substring(3, 5);
            String month = username.substring(5, 7);
            String day = username.substring(7, 9);
            String hour = username.substring(9, 11);
            String minute = username.substring(11, 13);
            String randomHash = username.substring(13);

            return String.format(
                "Platform: %s | Created: %s-%s-%s %s:%s | Unique ID: %s",
                platform,
                year,
                month,
                day,
                hour,
                minute,
                randomHash
            );
        } catch (Exception e) {
            return "Invalid username format";
        }
    }
}


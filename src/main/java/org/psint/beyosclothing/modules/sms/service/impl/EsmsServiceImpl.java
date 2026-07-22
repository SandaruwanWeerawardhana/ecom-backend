package org.psint.beyosclothing.modules.sms.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.adeonatech.dto.*;
import net.adeonatech.service.SendSMSImpl;
import org.psint.beyosclothing.modules.sms.config.EsmsProperties;
import org.psint.beyosclothing.modules.sms.service.EsmsSendResult;
import org.psint.beyosclothing.modules.sms.service.EsmsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * eSMS (Dialog Sri Lanka) service implementation.
 * Token lifecycle: obtained via {@code getToken()}, cached in-memory for
 * (expiry minus 5 min) seconds to avoid clock-edge races. Concurrent threads
 * share the same token and only one thread refreshes at a time (double-checked
 * locking with a {@link ReentrantLock}).
 * Rate limits (per API docs):
 *   - 20 TPS for SMS sends, enforced via a 50 ms minimum inter-send gap
 *   - 30 TPS general consumption
 *   - 2 TPS for status checks (not throttled here; callers should not poll aggressively)

 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsmsServiceImpl implements EsmsService {

    private final EsmsProperties properties;
    private final SendSMSImpl smsClient;

    @Qualifier("esmsRestTemplate")
    private final RestTemplate esmsRestTemplate;

    private volatile String cachedToken;
    private volatile Instant tokenExpiryTime = Instant.MIN;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private final AtomicLong lastSendMs = new AtomicLong(0);
    private static final long MIN_SEND_INTERVAL_MS = 50;

    // public API

    @Override
    public EsmsSendResult sendSms(List<String> mobileNumbers, String message, String transactionId) {
        if (!properties.isSendable()) {
            log.info("eSMS credentials not configured; skipping send for transaction: {}", transactionId);
            return EsmsSendResult.builder()
                    .success(false)
                    .errorCode("NOT_CONFIGURED")
                    .errorMessage("eSMS credentials are not configured")
                    .build();
        }

        List<String> normalized = mobileNumbers.stream()
                .map(this::normalizePhoneNumber)
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toList());

        if (normalized.isEmpty()) {
            log.warn("No valid mobile numbers after normalization for transaction: {}", transactionId);
            return EsmsSendResult.builder()
                    .success(false)
                    .errorCode("109")
                    .errorMessage("No valid mobile numbers")
                    .build();
        }

        // Prefer the URL Message Key (GET) endpoint when a key is configured; it needs no token.
        if (properties.hasUrlMessageKey()) {
            return sendViaUrlMessageKey(normalized, message, transactionId);
        }

        return sendViaAccessToken(normalized, message, transactionId);
    }

    /**
     * Sends SMS through the eSMS "SMS via GET request" endpoint using the URL Message Key (esmsqk).
     * The response body is a bare integer: {@code 1} means success, any other value is an error id
     * (see section 3.2.3 of the eSMS API documentation).
     */
    private EsmsSendResult sendViaUrlMessageKey(List<String> normalized, String message, String transactionId) {
        URI uri = buildUrlCampaignUri(normalized, message);
        try {
            applyRateLimit();
            ResponseEntity<String> response = esmsRestTemplate.getForEntity(uri, String.class);
            lastSendMs.set(System.currentTimeMillis());

            String body = response.getBody() != null ? response.getBody().trim() : "";
            // Some deployments return "1|<balance>"; the status is the part before the pipe.
            String statusCode = body.contains("|") ? body.substring(0, body.indexOf('|')).trim() : body;

            if ("1".equals(statusCode)) {
                log.info("SMS sent via URL message key - transactionId: {}, recipients: {}", transactionId, normalized.size());
                return EsmsSendResult.builder()
                        .success(true)
                        .transactionId(transactionId)
                        .providerMessage("Accepted by eSMS URL message key endpoint")
                        .build();
            }

            String reason = describeGetError(statusCode);
            log.warn("eSMS URL-key send failed for transaction {} (errCode={}): {}", transactionId, statusCode, reason);
            return EsmsSendResult.builder().success(false).errorCode(statusCode).errorMessage(reason).build();

        } catch (Exception e) {
            log.warn("eSMS URL-key send failed for transaction {}: {}", transactionId, e.getMessage());
            return EsmsSendResult.builder().success(false).errorCode("IO_ERROR").errorMessage(e.getMessage()).build();
        }
    }

    /** Builds the fully-encoded url-campaign GET request URI for the URL Message Key send. */
    private URI buildUrlCampaignUri(List<String> normalized, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getUrlBaseUrl() + "/api/v1/message-via-url/create/url-campaign")
                .queryParam("esmsqk", properties.getUrlMessageKey())
                .queryParam("list", String.join(",", normalized))
                .queryParam("source_address", properties.getSourceAddress())
                .queryParam("message", message);

        String baseUrl = properties.getDeliveryReportBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.queryParam("push_notification_url", baseUrl + "/api/sms/delivery-report");
        }

        return builder.encode().build().toUri();
    }

    /** Sends SMS through the token-based "SMS via POST request" endpoint (username/password flow). */
    private EsmsSendResult sendViaAccessToken(List<String> normalized, String message, String transactionId) {
        try {
            String token = ensureValidToken();
            applyRateLimit();

            List<Msisdn> msisdns = normalized.stream().map(Msisdn::new).collect(Collectors.toList());

            ExtendedSendTextBody body = buildSendBody(transactionId, message, msisdns);
            SendTextResponse response = smsClient.sendText(body, token);
            lastSendMs.set(System.currentTimeMillis());

            if (response == null) {
                log.error("Null response from eSMS API for transaction: {}", transactionId);
                return EsmsSendResult.builder().success(false).errorCode("UNKNOWN").errorMessage("Null API response").build();
            }

            // Error 100 means our token expired mid-request; refresh and retry once
            if ("100".equals(response.getErrCode())) {
                log.warn("eSMS token expired mid-request (err 100), re-authenticating and retrying: {}", transactionId);
                invalidateToken();
                token = ensureValidToken();
                response = smsClient.sendText(body, token);
            }

            if (response == null || !"0".equals(response.getErrCode())) {
                String errCode = response != null ? response.getErrCode() : "UNKNOWN";
                String comment = response != null ? response.getComment() : "Null response after retry";
                logApiError(errCode, transactionId);
                return EsmsSendResult.builder().success(false).errorCode(errCode).errorMessage(comment).build();
            }

            long campaignId = response.getData() != null ? response.getData().getCampaignId() : 0L;
            log.info("SMS sent - campaignId: {}, transactionId: {}, recipients: {}", campaignId, transactionId, normalized.size());

            return EsmsSendResult.builder()
                    .success(true)
                    .campaignId(campaignId)
                    .transactionId(transactionId)
                    .providerMessage("Campaign ID: " + campaignId)
                    .build();

        } catch (IOException e) {
            log.warn("eSMS send failed for transaction {}: {}", transactionId, e.getMessage());
            return EsmsSendResult.builder().success(false).errorCode("IO_ERROR").errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public String checkCampaignStatus(String transactionId) {
        if (!properties.isConfigured()) {
            log.info("eSMS credentials not configured; cannot check status for transaction: {}", transactionId);
            return "NOT_CONFIGURED";
        }
        try {
            String token = ensureValidToken();

            TransactionBody body = new TransactionBody();
            body.setTransaction_id(transactionId);

            TransactionResponse response = smsClient.getTransactionIDStatus(body, token);

            if (response != null && "100".equals(response.getErrCode())) {
                invalidateToken();
                token = ensureValidToken();
                response = smsClient.getTransactionIDStatus(body, token);
            }

            if (response != null && response.getDataTransaction() != null) {
                return response.getDataTransaction().getCampaign_status();
            }
            return "UNKNOWN";

        } catch (IOException e) {
            log.error("Failed to check campaign status for transaction {}: {}", transactionId, e.getMessage());
            return "ERROR";
        }
    }

    // token management

    private String ensureValidToken() throws IOException {
        if (isTokenValid()) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            return refreshToken();
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenValid() {
        return cachedToken != null && Instant.now().isBefore(tokenExpiryTime);
    }

    private String refreshToken() throws IOException {
        log.info("Refreshing eSMS access token");
        TokenBody tokenBody = new TokenBody();
        tokenBody.setUsername(properties.getUsername());
        tokenBody.setPassword(properties.getPassword());

        TokenResponse response = smsClient.getToken(tokenBody);
        if (response == null || response.getToken() == null) {
            throw new IOException("Failed to obtain eSMS access token: null or empty response");
        }

        long expirySeconds = response.getExpiration() > 0
                ? response.getExpiration()
                : properties.getTokenExpirySeconds();
        // Subtract 5 minutes as safety buffer against clock skew
        cachedToken = response.getToken();
        tokenExpiryTime = Instant.now().plusSeconds(expirySeconds - 300);
        log.info("eSMS token refreshed, valid for {} s", expirySeconds - 300);
        return cachedToken;
    }

    private void invalidateToken() {
        tokenLock.lock();
        try {
            cachedToken = null;
            tokenExpiryTime = Instant.MIN;
        } finally {
            tokenLock.unlock();
        }
    }

    // helpers

    private ExtendedSendTextBody buildSendBody(String transactionId, String message, List<Msisdn> msisdns) {
        ExtendedSendTextBody body = new ExtendedSendTextBody();
        body.setTransaction_id(transactionId);
        body.setMessage(message);
        body.setSourceAddress(properties.getSourceAddress());
        body.setMsisdn(msisdns);

        String baseUrl = properties.getDeliveryReportBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            body.setPushNotificationUrl(baseUrl + "/api/sms/delivery-report");
        }
        return body;
    }

    /** Enforces the 20 TPS send limit by sleeping if the last send was too recent. */
    private void applyRateLimit() {
        long elapsed = System.currentTimeMillis() - lastSendMs.get();
        if (elapsed < MIN_SEND_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_SEND_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Normalises any common Sri Lanka format to the bare 9-digit number the API expects.
     * Accepts: 0771234567, 94771234567, +94771234567, 771234567.
     * Returns empty string if the result is not 9 digits (invalid / unsupported format).
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        String stripped = phone.trim().replaceAll("[\\s\\-()]", "");
        if (stripped.startsWith("+94")) {
            stripped = stripped.substring(3);
        } else if (stripped.startsWith("94") && stripped.length() == 11) {
            stripped = stripped.substring(2);
        } else if (stripped.startsWith("0")) {
            stripped = stripped.substring(1);
        }
        if (stripped.length() != 9) {
            log.warn("Could not normalize phone number to 9-digit format: {}", phone);
            return "";
        }
        return stripped;
    }

    private void logApiError(String errCode, String transactionId) {
        String detail = switch (errCode) {
            case "100" -> "Token expired - re-authentication required";
            case "104" -> "Duplicate transaction ID - each send must use a unique transaction_id";
            case "109" -> "No valid mobile numbers - all supplied numbers were rejected";
            case "117" -> "Too many requests - exceeded 20 TPS (send) or 30 TPS (general) account limits";
            case "118" -> "System blackout - eSMS blocks sends between 8 PM and 8 AM";
            default    -> "Undocumented error code; consult eSMS API docs";
        };
        log.error("eSMS API error {} for transaction {}: {}", errCode, transactionId, detail);
    }

    /** Maps a "SMS via GET request" response id to a human-readable reason (section 3.2.3). */
    private String describeGetError(String responseId) {
        return switch (responseId) {
            case "2001" -> "Error occurred during campaign creation";
            case "2002" -> "Bad request";
            case "2003" -> "Empty number list";
            case "2004" -> "Empty message body";
            case "2005" -> "Invalid number list format";
            case "2006" -> "Not eligible to send messages via GET requests - admin has not granted access";
            case "2007" -> "Invalid URL message key (esmsqk)";
            case "2008" -> "Not enough wallet balance or package messages remaining";
            case "2009" -> "No valid numbers after removing mask-blocked numbers";
            case "2010" -> "Not eligible to consume packaging";
            case "2011" -> "Transactional error";
            case "2012" -> "No access for the specified mask";
            case "2013" -> "System blackout period (generally 08:00 PM - 08:00 AM)";
            case "2020" -> "Too many requests";
            default -> "Undocumented response id; consult eSMS API docs";
        };
    }

    // inner types

    /**
     * Extends the library's {@link SendTextBody} with the {@code push_notification_url} field,
     * which the eSMS API supports but the library does not expose.
     * Jackson serialises the actual runtime type, so the extra field is included in the POST body.
     */
    private static class ExtendedSendTextBody extends SendTextBody {

        @JsonProperty("push_notification_url")
        private String pushNotificationUrl;

        public void setPushNotificationUrl(String url) {
            this.pushNotificationUrl = url;
        }

        public String getPushNotificationUrl() {
            return pushNotificationUrl;
        }
    }
}

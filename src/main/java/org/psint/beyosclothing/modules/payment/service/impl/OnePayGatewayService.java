package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.config.OnePayProperties;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayRequest;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayResponse;
import org.psint.beyosclothing.modules.payment.service.PaymentGatewayService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OnePay Gateway Service Implementation.
 * API reference: https://docs.onepay.lk/api-documentation
 */
@Service
@Slf4j
public class OnePayGatewayService implements PaymentGatewayService {

    private static final String GATEWAY_NAME = "OnePay";

    /** OnePay rejects a null customer_email; used when the order has no email on file so payment can still proceed. */
    private static final String FALLBACK_CUSTOMER_EMAIL = "noreply@beyosclothing.com";

    /** OnePay rejects null customer_first_name/customer_last_name; used when the customer has no name on file. */
    private static final String FALLBACK_CUSTOMER_NAME = "Customer";

    private final OnePayProperties properties;
    private final RestTemplate onePayRestTemplate;

    public OnePayGatewayService(OnePayProperties properties,
                                 @Qualifier("onePayRestTemplate") RestTemplate onePayRestTemplate) {
        this.properties = properties;
        this.onePayRestTemplate = onePayRestTemplate;
    }

    @Override
    public PaymentGatewayResponse initiatePayment(PaymentGatewayRequest request) {
        log.info("Initiating OnePay payment - Order: {}, Amount: {}", request.getOrderNumber(), request.getAmount());

        if (!properties.isConfigured()) {
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .errorMessage("OnePay is not configured")
                    .build();
        }

        try {
            String currency = request.getCurrency() != null ? request.getCurrency() : "LKR";
            String amountStr = request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String hash = sha256Hex(properties.getAppId() + currency + amountStr + properties.getHashSalt());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", properties.getAppId());
            // OnePay expects amount as a numeric value with two decimals (e.g. 1500.00), not a string.
            body.put("amount", request.getAmount().setScale(2, RoundingMode.HALF_UP));
            body.put("currency", currency);
            body.put("hash", hash);
            body.put("reference", request.getPaymentRequestUuid());

            String firstName = firstNonBlank(request.getCustomerFirstName(), FALLBACK_CUSTOMER_NAME);
            String lastName = firstNonBlank(request.getCustomerLastName(), firstName);
            body.put("customer_first_name", firstName);
            body.put("customer_last_name", lastName);
            body.put("customer_phone_number", normalizePhone(request.getCustomerPhone()));
            body.put("customer_email", resolveCustomerEmail(request.getCustomerEmail()));
            body.put("transaction_redirect_url", request.getReturnUrl());
            body.put("additionalData", request.getPaymentRequestUuid());

            // OnePay v3 nests the result under "data": { "ipg_transaction_id", "gateway": { "redirect_url" } }.
            Map<String, Object> responseBody = postJson(properties.getBaseUrl() + "/v3/checkout/link/", body);
            Map<String, Object> data = asMap(responseBody != null ? responseBody.get("data") : null);
            Map<String, Object> gateway = asMap(data != null ? data.get("gateway") : null);
            String redirectUrl = gateway != null ? stringOrNull(gateway.get("redirect_url")) : null;

            if (redirectUrl == null) {
                log.warn("OnePay checkout link creation returned no redirect_url - Order: {}, Response: {}",
                        request.getOrderNumber(), responseBody);
                return PaymentGatewayResponse.builder()
                        .success(false)
                        .errorMessage("OnePay did not return a redirect URL")
                        .build();
            }

            String ipgTransactionId = data != null ? stringOrNull(data.get("ipg_transaction_id")) : null;

            return PaymentGatewayResponse.builder()
                    .success(true)
                    .transactionId(ipgTransactionId)
                    .redirectUrl(redirectUrl)
                    .status("PENDING")
                    .amount(request.getAmount())
                    .build();

        } catch (HttpStatusCodeException e) {
            log.error("OnePay checkout link creation failed - Order: {}, Status: {}, Body: {}",
                    request.getOrderNumber(), e.getStatusCode(), e.getResponseBodyAsString());
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .errorCode(String.valueOf(e.getStatusCode().value()))
                    .errorMessage(describeError(e.getStatusCode().value(), e.getResponseBodyAsString()))
                    .build();
        } catch (Exception e) {
            log.error("Error initiating OnePay payment - Order: {}", request.getOrderNumber(), e);
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .errorMessage("Error initiating OnePay payment: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Server-side re-verification via OnePay's Transaction Status API. The request sends
     * {app_id, onepay_transaction_id}; the response nests the outcome under "data", where the
     * boolean "status" field indicates whether the payment was completed. A transport-level
     * failure here is NOT proof the payment failed - callers should fall back to the callback's
     * own reported status rather than blocking on it.
     */
    @Override
    public PaymentGatewayResponse verifyPayment(String transactionId, String signature) {
        log.info("Verifying OnePay payment - Transaction ID: {}", transactionId);

        if (!properties.isConfigured() || transactionId == null || transactionId.isBlank()) {
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .errorMessage("OnePay is not configured or transactionId is missing")
                    .build();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", properties.getAppId());
            body.put("onepay_transaction_id", transactionId);

            // OnePay v3 status result nests under "data", where the boolean "status" is the paid flag.
            Map<String, Object> responseBody = postJson(properties.getBaseUrl() + "/v3/transaction/status/", body);
            Map<String, Object> data = asMap(responseBody != null ? responseBody.get("data") : null);
            boolean success = isPaid(data != null ? data.get("status") : null);

            return PaymentGatewayResponse.builder()
                    .success(success)
                    .transactionId(transactionId)
                    .status(success ? "PAID" : "FAILED")
                    .build();

        } catch (Exception e) {
            log.warn("OnePay transaction status verification failed (non-fatal) - Transaction ID: {}: {}",
                    transactionId, e.getMessage());
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .transactionId(transactionId)
                    .errorMessage("Verification call failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String getGatewayName() {
        return GATEWAY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return properties.isConfigured();
    }

    private Map<String, Object> postJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, properties.getAppToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = onePayRestTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private String describeError(int statusCode, String body) {
        return switch (statusCode) {
            case 400 -> "Invalid request - check app ID, amount, or currency (" + body + ")";
            case 401 -> "Authentication failed - check hash/app ID (" + body + ")";
            case 429 -> "Rate limited by OnePay - too many requests";
            default -> "OnePay error " + statusCode + ": " + body;
        };
    }

    private String resolveCustomerEmail(String email) {
        return (email != null && !email.isBlank()) ? email : FALLBACK_CUSTOMER_EMAIL;
    }

    private String firstNonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String trimmed = phone.trim().replaceAll("[\\s-]", "");
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("0")) {
            return "+94" + trimmed.substring(1);
        }
        if (trimmed.startsWith("94")) {
            return "+" + trimmed;
        }
        return "+94" + trimmed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isBlank() || "null".equals(str) ? null : str;
    }

    private boolean isPaid(Object status) {
        if (status instanceof Boolean) {
            return (Boolean) status;
        }
        if (status == null) {
            return false;
        }
        String str = String.valueOf(status);
        return "1".equals(str) || "true".equalsIgnoreCase(str) || "SUCCESS".equalsIgnoreCase(str);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

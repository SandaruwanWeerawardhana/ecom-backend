package org.psint.beyosclothing.modules.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "onepay")
public class OnePayProperties {

    /** OnePay merchant App ID. May be blank in non-production environments; the gateway is then disabled rather than failing startup. */
    private String appId;

    /** OnePay App Token, sent as the Authorization header on every API call. Never sent to clients. */
    private String appToken;

    /** OnePay Hash Salt, used server-side only to sign requests. Never sent to OnePay or exposed to clients. */
    private String hashSalt;

    /** Base URL of the OnePay API. */
    private String baseUrl = "https://api.onepay.lk";

    /** True only when appId, appToken and hashSalt are all present, i.e. the gateway is usable. */
    public boolean isConfigured() {
        return hasRealValue(appId) && hasRealValue(appToken) && hasRealValue(hashSalt);
    }

    private boolean hasRealValue(String value) {
        return value != null && !value.isBlank();
    }
}

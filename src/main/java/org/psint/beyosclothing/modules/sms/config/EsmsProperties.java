package org.psint.beyosclothing.modules.sms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "esms")
public class EsmsProperties {

    /**
     * eSMS account username. May be blank in non-production environments where
     * SMS sending is not configured; sends are then skipped rather than failing startup.
     */
    private String username;

    /** eSMS account password. May be blank when SMS sending is not configured. */
    private String password;

    /** Sender mask registered with Dialog eSMS (max 11 chars). */
    private String sourceAddress = "BeyosCloth";

    /**
     * URL Message Key (esmsqk) generated in the eSMS portal. When present, SMS are sent
     * via the "SMS via GET request" endpoint, which needs only this key — no username,
     * password or access token. Generated under the "URL Message Key" section of the portal.
     */
    private String urlMessageKey;

    /**
     * Base URL of the eSMS "SMS via GET request" API. Sends hit
     * {@code <urlBaseUrl>/api/v1/message-via-url/create/url-campaign}.
     */
    private String urlBaseUrl = "https://e-sms.dialog.lk";

    /**
     * Base URL of this application, used to build the push_notification_url sent to eSMS.
     * Example: https://api.beyos.com
     */
    private String deliveryReportBaseUrl;

    /** Token TTL in seconds. eSMS tokens last ~43 200 s (12 h); defaults to that. */
    private long tokenExpirySeconds = 43200;

    /** True only when both real credentials are present, i.e. token-based SMS sending is usable. */
    public boolean isConfigured() {
        return hasRealValue(username, "947XXXXXXXX")
                && hasRealValue(password, "YourPassword");
    }

    /** True when a real URL Message Key is present, i.e. GET-based SMS sending is usable. */
    public boolean hasUrlMessageKey() {
        return hasRealValue(urlMessageKey, "YourUrlMessageKey");
    }

    /** True when SMS can be sent by either the URL Message Key or username/password credentials. */
    public boolean isSendable() {
        return hasUrlMessageKey() || isConfigured();
    }

    private boolean hasRealValue(String value, String placeholder) {
        return value != null
                && !value.isBlank()
                && !placeholder.equalsIgnoreCase(value.trim());
    }
}

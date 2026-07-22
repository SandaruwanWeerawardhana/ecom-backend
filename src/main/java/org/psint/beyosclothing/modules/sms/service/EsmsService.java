package org.psint.beyosclothing.modules.sms.service;

import java.util.List;

public interface EsmsService {

    /**
     * Sends a text message to one or more mobile numbers.
     *
     * @param mobileNumbers recipient numbers in any common Sri Lanka format
     * @param message       plain-text message body (max 1 600 chars)
     * @param transactionId caller-supplied unique ID; must differ across retries to avoid error 104
     */
    EsmsSendResult sendSms(List<String> mobileNumbers, String message, String transactionId);

    /**
     * Checks the delivery status of a previously submitted campaign by its transaction ID.
     *
     * @return campaign status string, or "UNKNOWN" / "ERROR" on failure
     */
    String checkCampaignStatus(String transactionId);
}

package org.psint.beyosclothing.modules.sms.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EsmsSendResult {

    private final boolean success;
    private final long campaignId;
    private final String transactionId;
    private final String providerMessage;
    private final String errorCode;
    private final String errorMessage;
}

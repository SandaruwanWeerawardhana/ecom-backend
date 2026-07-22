package org.psint.beyosclothing.modules.sms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.sms.dto.request.SmsSendRequest;
import org.psint.beyosclothing.modules.sms.dto.response.SmsSendResponse;
import org.psint.beyosclothing.modules.sms.service.EsmsSendResult;
import org.psint.beyosclothing.modules.sms.service.EsmsService;
import org.psint.beyosclothing.modules.sms.service.SmsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final EsmsService esmsService;

    @Override
    public SmsSendResponse sendSms(SmsSendRequest request) {
        String transactionId = generateProviderTransactionId();
        log.info("Admin SMS send request to: {}", request.getRecipientPhone());

        EsmsSendResult result = esmsService.sendSms(
                List.of(request.getRecipientPhone()),
                request.getMessage(),
                transactionId
        );

        String status = result.isSuccess() ? "SENT" : "FAILED";
        String providerMessage = result.isSuccess()
                ? getSuccessProviderMessage(result)
                : result.getErrorMessage();

        return SmsSendResponse.builder()
                .uuid(UUID.randomUUID().toString())
                .recipientPhone(request.getRecipientPhone())
                .status(status)
                .providerMessage(providerMessage)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    private String getSuccessProviderMessage(EsmsSendResult result) {
        if (result.getProviderMessage() != null && !result.getProviderMessage().isBlank()) {
            return result.getProviderMessage();
        }
        return result.getCampaignId() > 0 ? "Campaign ID: " + result.getCampaignId() : "SMS accepted by provider";
    }

    private String generateProviderTransactionId() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return System.currentTimeMillis() + String.valueOf(suffix);
    }
}

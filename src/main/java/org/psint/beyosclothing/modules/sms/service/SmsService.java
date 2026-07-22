package org.psint.beyosclothing.modules.sms.service;

import jakarta.validation.Valid;
import org.psint.beyosclothing.modules.sms.dto.request.SmsSendRequest;
import org.psint.beyosclothing.modules.sms.dto.response.SmsSendResponse;

public interface SmsService {

    SmsSendResponse sendSms(@Valid SmsSendRequest request);
}

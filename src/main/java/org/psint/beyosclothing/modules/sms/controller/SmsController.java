package org.psint.beyosclothing.modules.sms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.sms.dto.request.SmsSendRequest;
import org.psint.beyosclothing.modules.sms.dto.response.SmsSendResponse;
import org.psint.beyosclothing.modules.sms.service.SmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sms")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SMS Controller", description = "APIs for sending SMS messages")
public class SmsController {

    private final SmsService smsService;

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Send SMS",
            description = "Accepts an SMS send request and passes it to the SMS service.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<SmsSendResponse> sendSms(@Valid @RequestBody SmsSendRequest request) {
        log.info("Sending SMS request to recipient: {}", request.getRecipientPhone());
        SmsSendResponse response = smsService.sendSms(request);
        log.info("SMS request processed with UUID: {}", response.getUuid());
        return ResponseEntity.ok(response);
    }
}

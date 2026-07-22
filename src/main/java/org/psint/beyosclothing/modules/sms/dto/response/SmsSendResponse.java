package org.psint.beyosclothing.modules.sms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsSendResponse {

    private String uuid;
    private String recipientPhone;
    private String status;
    private String providerMessage;
    private LocalDateTime requestedAt;
}

package org.psint.beyosclothing.modules.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Koombiyo Waybills (Barcodes) API.
 * Maps the response: {"waybills":[{"waybill_id":"459654070"}, ...]}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoombiyoWaybillResponse {

    private List<Waybill> waybills;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Waybill {

        @JsonProperty("waybill_id")
        private String waybillId;
    }
}


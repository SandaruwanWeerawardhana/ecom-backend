package org.psint.beyosclothing.modules.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Koombiyo Districts API.
 * Maps the array response: [{"district_id":"1","district_name":"Ampara"}, ...]
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KoombiyoDistrictResponse {

    private List<District> districts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class District {

        @JsonProperty("district_id")
        private String districtId;

        @JsonProperty("district_name")
        private String districtName;
    }
}

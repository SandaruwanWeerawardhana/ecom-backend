package org.psint.beyosclothing.modules.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Koombiyo Cities API.
 * Maps the array response: [{"city_id":"652","city_name":"Addalaichenai"}, ...]
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KoombiyoCityResponse {

    private List<City> cities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class City {

        @JsonProperty("city_id")
        private String cityId;

        @JsonProperty("city_name")
        private String cityName;
    }
}

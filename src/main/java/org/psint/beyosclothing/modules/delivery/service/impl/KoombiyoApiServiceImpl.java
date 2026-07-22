package org.psint.beyosclothing.modules.delivery.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoCityResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoDistrictResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoWaybillResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierApiLog;
import org.psint.beyosclothing.modules.delivery.repository.CourierApiLogRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.service.KoombiyoApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KoombiyoApiServiceImpl implements KoombiyoApiService {

    private static final String DISTRICTS_PATH = "Districts/users";
    private static final String CITIES_PATH    = "Cities/users";
    private static final String WAYBILLS_PATH  = "Waybils/users";

    private final CourierRepository courierRepository;
    private final CourierApiLogRepository courierApiLogRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("deliveryRestTemplate")
    private RestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // Public API methods
    // -------------------------------------------------------------------------

    @Override
    @Transactional("deliveryTransactionManager")
    public KoombiyoDistrictResponse getDistricts() {
        Courier courier = resolveActiveCourier();
        String endpoint = buildEndpoint(courier, DISTRICTS_PATH);

        log.info("Calling Koombiyo Districts API for courier [{}] -> {}", courier.getCode(), endpoint);

        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("apikey", courier.getApiKey());

        String requestPayload = "apikey=" + courier.getApiKey();
        String rawResponse = null;
        int httpStatus = 0;

        try {
            ResponseEntity<String> response = postForm(endpoint, formBody);
            httpStatus  = response.getStatusCode().value();
            rawResponse = response.getBody();

            log.info("Koombiyo Districts API response [HTTP {}] for courier [{}]:\n{}",
                    httpStatus, courier.getCode(), rawResponse);

            List<KoombiyoDistrictResponse.District> districts = objectMapper.readValue(
                    rawResponse,
                    new TypeReference<List<KoombiyoDistrictResponse.District>>() {}
            );

            return KoombiyoDistrictResponse.builder()
                    .districts(districts)
                    .build();

        } catch (HttpStatusCodeException ex) {
            httpStatus  = ex.getStatusCode().value();
            rawResponse = ex.getResponseBodyAsString();
            log.error("Koombiyo Districts API error [HTTP {}] for courier [{}]: {}",
                    httpStatus, courier.getCode(), rawResponse);
            return KoombiyoDistrictResponse.builder()
                    .districts(Collections.emptyList())
                    .build();
        } catch (Exception ex) {
            httpStatus  = 0;
            rawResponse = ex.getMessage();
            log.error("Unexpected error calling Koombiyo Districts API for courier [{}]: {}",
                    courier.getCode(), ex.getMessage(), ex);
            return KoombiyoDistrictResponse.builder()
                    .districts(Collections.emptyList())
                    .build();
        } finally {
            saveApiLog(courier, endpoint, requestPayload, rawResponse, httpStatus);
        }
    }

    @Override
    @Transactional("deliveryTransactionManager")
    public KoombiyoCityResponse getCitiesByDistrict(Integer districtId) {
        Courier courier = resolveActiveCourier();
        String endpoint = buildEndpoint(courier, CITIES_PATH);

        log.info("Calling Koombiyo Cities API for courier [{}], districtId={} -> {}",
                courier.getCode(), districtId, endpoint);

        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("apikey", courier.getApiKey());
        formBody.add("district_id", String.valueOf(districtId));

        String requestPayload = "apikey=" + courier.getApiKey() + "&district_id=" + districtId;
        String rawResponse = null;
        int httpStatus = 0;

        try {
            ResponseEntity<String> response = postForm(endpoint, formBody);
            httpStatus  = response.getStatusCode().value();
            rawResponse = response.getBody();

            log.info("Koombiyo Cities API response [HTTP {}] for courier [{}], districtId={}:\n{}",
                    httpStatus, courier.getCode(), districtId, rawResponse);

            List<KoombiyoCityResponse.City> cities = objectMapper.readValue(
                    rawResponse,
                    new TypeReference<List<KoombiyoCityResponse.City>>() {}
            );

            return KoombiyoCityResponse.builder()
                    .cities(cities)
                    .build();

        } catch (HttpStatusCodeException ex) {
            httpStatus  = ex.getStatusCode().value();
            rawResponse = ex.getResponseBodyAsString();
            log.error("Koombiyo Cities API error [HTTP {}] for courier [{}], districtId={}: {}",
                    httpStatus, courier.getCode(), districtId, rawResponse);
            return KoombiyoCityResponse.builder()
                    .cities(Collections.emptyList())
                    .build();
        } catch (Exception ex) {
            httpStatus  = 0;
            rawResponse = ex.getMessage();
            log.error("Unexpected error calling Koombiyo Cities API for courier [{}], districtId={}: {}",
                    courier.getCode(), districtId, ex.getMessage(), ex);
            return KoombiyoCityResponse.builder()
                    .cities(Collections.emptyList())
                    .build();
        } finally {
            saveApiLog(courier, endpoint, requestPayload, rawResponse, httpStatus);
        }
    }

    @Override
    @Transactional("deliveryTransactionManager")
    public KoombiyoWaybillResponse getWaybills(Integer limit) {
        Courier courier = resolveActiveCourier();
        String endpoint = buildEndpoint(courier, WAYBILLS_PATH);

        log.info("Calling Koombiyo Waybills API for courier [{}], limit={} -> {}",
                courier.getCode(), limit, endpoint);

        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("apikey", courier.getApiKey());
        formBody.add("limit", String.valueOf(limit != null ? limit : 1));

        String requestPayload = "apikey=" + courier.getApiKey() + "&limit=" + (limit != null ? limit : 1);
        String rawResponse = null;
        int httpStatus = 0;

        try {
            ResponseEntity<String> response = postForm(endpoint, formBody);
            httpStatus  = response.getStatusCode().value();
            rawResponse = response.getBody();

            log.info("Koombiyo Waybills API response [HTTP {}] for courier [{}], limit={}:\n{}",
                    httpStatus, courier.getCode(), limit, rawResponse);

            KoombiyoWaybillResponse waybillResponse = objectMapper.readValue(
                    rawResponse,
                    KoombiyoWaybillResponse.class
            );

            return waybillResponse;

        } catch (HttpStatusCodeException ex) {
            httpStatus  = ex.getStatusCode().value();
            rawResponse = ex.getResponseBodyAsString();
            log.error("Koombiyo Waybills API error [HTTP {}] for courier [{}], limit={}: {}",
                    httpStatus, courier.getCode(), limit, rawResponse);
            return KoombiyoWaybillResponse.builder()
                    .waybills(Collections.emptyList())
                    .build();
        } catch (Exception ex) {
            httpStatus  = 0;
            rawResponse = ex.getMessage();
            log.error("Unexpected error calling Koombiyo Waybills API for courier [{}], limit={}: {}",
                    courier.getCode(), limit, ex.getMessage(), ex);
            return KoombiyoWaybillResponse.builder()
                    .waybills(Collections.emptyList())
                    .build();
        } finally {
            saveApiLog(courier, endpoint, requestPayload, rawResponse, httpStatus);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Automatically resolves the first active courier from the database.
     * Throws if no active courier exists.
     */
    private Courier resolveActiveCourier() {
        return courierRepository.findAllByIsActiveTrue()
                .stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No active courier found in the database for Koombiyo API call");
                    return new IllegalStateException(
                            "No active courier found. Please configure an active courier in the system.");
                });
    }

    /**
     * Build the full endpoint URL from the courier's apiBaseUrl and the path suffix.
     * Falls back to the Koombiyo default base URL if apiBaseUrl is not set on the courier.
     */
    private String buildEndpoint(Courier courier, String path) {
        String base = (courier.getApiBaseUrl() != null && !courier.getApiBaseUrl().isBlank())
                ? courier.getApiBaseUrl()
                : "https://application.koombiyodelivery.lk/api/";

        // Ensure the base URL ends with '/'
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + path;
    }

    /**
     * POST application/x-www-form-urlencoded to the given endpoint.
     */
    private ResponseEntity<String> postForm(String url, MultiValueMap<String, String> formBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formBody, headers);
        return restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * Persist a CourierApiLog entry for every external call regardless of success/failure.
     */
    private void saveApiLog(Courier courier, String endpoint,
                            String requestPayload, String responsePayload, int httpStatus) {
        try {
            CourierApiLog apiLog = CourierApiLog.builder()
                    .courier(courier)
                    .endpoint(endpoint)
                    .requestPayload(requestPayload)
                    .responsePayload(responsePayload)
                    .httpStatus(httpStatus == 0 ? null : httpStatus)
                    .build();
            courierApiLogRepository.save(apiLog);
            log.debug("Saved CourierApiLog for endpoint: {} with HTTP status: {}", endpoint, httpStatus);
        } catch (Exception ex) {
            // Never let logging failure propagate
            log.error("Failed to save CourierApiLog for endpoint {}: {}", endpoint, ex.getMessage(), ex);
        }
    }
}

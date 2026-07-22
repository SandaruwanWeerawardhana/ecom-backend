package org.psint.beyosclothing.modules.delivery.service;

import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoCityResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoDistrictResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoWaybillResponse;

/**
 * Service for calling Koombiyo Delivery external APIs.
 * The active Koombiyo courier and its apiKey are resolved automatically from the database.
 * All calls are logged to the CourierApiLog table.
 */
public interface KoombiyoApiService {

    /**
     * Fetch all districts from Koombiyo API.
     * POST https://application.koombiyodelivery.lk/api/Districts/users
     * The active courier is resolved automatically from the database.
     *
     * @return Raw response wrapped in KoombiyoDistrictResponse
     */
    KoombiyoDistrictResponse getDistricts();

    /**
     * Fetch all cities for a given district from Koombiyo API.
     * POST https://application.koombiyodelivery.lk/api/Cities/users
     * The active courier is resolved automatically from the database.
     *
     * @param districtId District ID obtained from the Districts API
     * @return Raw response wrapped in KoombiyoCityResponse
     */
    KoombiyoCityResponse getCitiesByDistrict(Integer districtId);

    /**
     * Fetch allocated waybill IDs (barcodes) from Koombiyo API.
     * POST https://application.koombiyodelivery.lk/api/Waybils/users
     * The active courier is resolved automatically from the database.
     *
     * @param limit Number of waybills to retrieve (default: 1)
     * @return Raw response wrapped in KoombiyoWaybillResponse
     */
    KoombiyoWaybillResponse getWaybills(Integer limit);
}

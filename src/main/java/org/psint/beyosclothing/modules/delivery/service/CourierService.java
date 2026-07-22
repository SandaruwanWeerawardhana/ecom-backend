package org.psint.beyosclothing.modules.delivery.service;

import jakarta.validation.Valid;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierRateResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierResponse;

import java.util.List;

public interface CourierService {
    CourierResponse createCourier(@Valid CreateCourierRequest request);

    CourierResponse updateCourier(String uuid, @Valid UpdateCourierRequest request);

    CourierResponse getCourierByUuid(String uuid);

    List<CourierResponse> getAllActiveCouriers();

    void deleteCourier(String uuid);

    CourierResponse toggleCourierStatus(String uuid);

    // Courier Rate Methods
    CourierRateResponse createCourierRate(@Valid CreateCourierRateRequest request);

    CourierRateResponse updateCourierRate(String uuid, @Valid UpdateCourierRateRequest request);

    CourierRateResponse getCourierRateByUuid(String uuid);

    List<CourierRateResponse> getCourierRatesByCourier(String courierUuid);

    void deleteCourierRate(String uuid);

    CourierRateResponse toggleCourierRateStatus(String uuid);
}

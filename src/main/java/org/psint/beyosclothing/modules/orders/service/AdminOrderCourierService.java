package org.psint.beyosclothing.modules.orders.service;

import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderWithCourierRequest;
import org.psint.beyosclothing.modules.orders.dto.request.AddPickupRequestDto;
import org.psint.beyosclothing.modules.orders.dto.response.PlaceOrderWithCourierResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AddPickupRequestResponse;

/**
 * Service interface for placing orders with courier
 */
public interface AdminOrderCourierService {

    /**
     * Place an order with a courier service (Koombiyo)
     * This API is called by admin to initiate courier pickup for an order
     *
     * @param request Request containing order UUID, waybill ID, and special notes
     * @return Response with shipment details and status
     */
    PlaceOrderWithCourierResponse placeOrderWithCourier(PlaceOrderWithCourierRequest request);

    /**
     * Add pickup request to Koombiyo courier
     * This API is called by admin to request pickup from a specific location
     *
     * @param request Request containing shipment UUID, vehicle type, address, and pickup details
     * @return Response with pickup request status and Koombiyo API response details
     */
    AddPickupRequestResponse addPickupRequest(AddPickupRequestDto request);
}

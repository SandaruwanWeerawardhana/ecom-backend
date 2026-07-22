package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.pos.dto.request.PosDeliveryOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosReceiptRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptDataResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface PosOrderService {

    PosOrderResponse placeOrder(PosPlaceOrderRequest request, String terminalHeader, String cashierHeader);

    PosOrderResponse placeDeliveryOrder(PosDeliveryOrderRequest request, String customerUuid, String guestToken);

    PosOrderResponse getOrderByUuid(String orderUuid, String terminalHeader, String cashierHeader);

    PageResponse<PosOrderResponse> listOrders(Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo, Pageable pageable);

    PosReceiptDataResponse getReceiptForOrder(String orderUuid);

    PosReceiptResponse createReceipt(PosReceiptRequest request);
}

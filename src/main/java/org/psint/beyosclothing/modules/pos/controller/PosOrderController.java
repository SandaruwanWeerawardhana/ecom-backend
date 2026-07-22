package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.pos.dto.request.PosDeliveryOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosReceiptRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptDataResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptResponse;
import org.psint.beyosclothing.modules.pos.service.PosOrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/pos/orders")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POS Orders", description = "Endpoints to place and retrieve POS orders")
public class PosOrderController {

    private final PosOrderService orderService;

    public static final String HEADER_TERMINAL = "X-Terminal-UUID";
    public static final String HEADER_CASHIER = "X-Cashier-UUID";

    @PostMapping
    @Operation(summary = "Place POS order (checkout)", description = "Transactional endpoint to place a POS order. Returns created order response.")
    public ResponseEntity<APIResponse<PosOrderResponse>> placeOrder(
            @RequestHeader(HEADER_TERMINAL) String terminalHeader,
            @RequestHeader(HEADER_CASHIER) String cashierHeader,
            @Valid @RequestBody PosPlaceOrderRequest request
    ) {
        log.info("placeOrder called by terminal={}, cashier={}", terminalHeader, cashierHeader);
        PosOrderResponse resp = orderService.placeOrder(request, terminalHeader, cashierHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<PosOrderResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Order placed successfully")
                .data(resp)
                .build());
    }

    @PostMapping("/delivery")
    @Operation(summary = "Place POS delivery order", description = "Creates a delivery order from a POS cart using POS cart items.")
    public ResponseEntity<APIResponse<PosOrderResponse>> placeDeliveryOrder(
            @RequestHeader(value = "X-Customer-UUID", required = false) String customerUuid,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody PosDeliveryOrderRequest request
    ) {
        log.info("placeDeliveryOrder called by terminal={}, cashier={}", customerUuid, guestToken);
        PosOrderResponse resp = orderService.placeDeliveryOrder(request, customerUuid, guestToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<PosOrderResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("POS delivery order placed successfully")
                .data(resp)
                .build());
    }

    @GetMapping("/{orderUuid}")
    @Operation(summary = "Get POS order details by UUID")
    public ResponseEntity<APIResponse<PosOrderResponse>> getOrder(@PathVariable String orderUuid,
                                                                  @RequestHeader(HEADER_TERMINAL) String terminalHeader,
                                                                  @RequestHeader(HEADER_CASHIER) String cashierHeader) {
        log.debug("getOrder orderUuid={}, terminal={}, cashier={}", orderUuid, terminalHeader, cashierHeader);
        PosOrderResponse resp = orderService.getOrderByUuid(orderUuid, terminalHeader, cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosOrderResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Order retrieved")
                .data(resp)
                .build());
    }

    @GetMapping
    @Operation(summary = "List POS orders with filters and pagination")
    public ResponseEntity<APIResponse<PageResponse<PosOrderResponse>>> listOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        log.debug("listOrders called. filters dateFrom={}, dateTo={}, page={}, size={}",
                dateFrom, dateTo, page, size);

        Pageable pageable = PageRequest.of(page, size);
        // default date range utils: if both null, leave null (service can interpret 'today' if required)
        Optional<LocalDate> fromOpt = Optional.ofNullable(dateFrom);
        Optional<LocalDate> toOpt = Optional.ofNullable(dateTo);

        PageResponse<PosOrderResponse> resp = orderService.listOrders(fromOpt, toOpt, pageable);
        return ResponseEntity.ok(APIResponse.<PageResponse<PosOrderResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Orders retrieved")
                .data(resp)
                .build());
    }

    @GetMapping("/{orderUuid}/receipt")
    @Operation(summary = "Get receipt data for printing (thermal)")
    public ResponseEntity<APIResponse<PosReceiptDataResponse>> getReceipt(@PathVariable String orderUuid) {
        log.debug("getReceipt orderUuid={}", orderUuid);
        PosReceiptDataResponse receiptData = orderService.getReceiptForOrder(orderUuid);
        return ResponseEntity.ok(APIResponse.<PosReceiptDataResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Receipt retrieved")
                .data(receiptData)
                .build());
    }

    @PostMapping("/receipts")
    @Operation(summary = "Create POS receipt", description = "Adds receipt print date data to the POS receipt table.")
    public ResponseEntity<APIResponse<PosReceiptResponse>> createReceipt(
            @Valid @RequestBody PosReceiptRequest request
    ) {
        log.info("createReceipt called for orderUuid={}", request.getOrderUuid());
        PosReceiptResponse response = orderService.createReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<PosReceiptResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Receipt created successfully")
                .data(response)
                .build());
    }
}

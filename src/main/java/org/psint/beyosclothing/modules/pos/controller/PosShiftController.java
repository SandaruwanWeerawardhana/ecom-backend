package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.pos.dto.request.CloseShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.request.OpenShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftSummaryResponse;
import org.psint.beyosclothing.modules.pos.service.PosShiftService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pos/shifts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POS Shifts", description = "Shift management and cash drawer operations")
public class PosShiftController {

    private final PosShiftService shiftService;
    public static final String HEADER_CASHIER = "X-Cashier-UUID";

    @PostMapping("/open")
    @Operation(summary = "Open a new shift for cashier")
    public ResponseEntity<APIResponse<PosShiftResponse>> openShift(@RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                    @Valid @RequestBody OpenShiftRequest request) {
        log.info("openShift called by cashierHeader={}", cashierHeader);
        // basic validation: check header matches request cashier id if provided as UUID mapping
        PosShiftResponse resp = shiftService.openShift(request);
        return ResponseEntity.ok(APIResponse.<PosShiftResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Shift opened")
                .data(resp)
                .build());
    }

    @PostMapping("/{shiftUuid}/close")
    @Operation(summary = "Close an active shift")
    public ResponseEntity<APIResponse<PosShiftResponse>> closeShift(@RequestHeader(HEADER_CASHIER) String cashierHeader,
                                                                     @PathVariable String shiftUuid,
                                                                     @Valid @RequestBody CloseShiftRequest request) {
        log.info("closeShift called shiftUuid={} by cashierHeader={}", shiftUuid, cashierHeader);
        PosShiftResponse resp = shiftService.closeShift(shiftUuid, request);
        return ResponseEntity.ok(APIResponse.<PosShiftResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Shift closed")
                .data(resp)
                .build());
    }

    @GetMapping("/current")
    @Operation(summary = "Get current active shift for cashier")
    public ResponseEntity<APIResponse<PosShiftResponse>> getCurrentShift(@RequestHeader(HEADER_CASHIER) String cashierHeader) {
        log.debug("getCurrentShift called cashierHeader={}", cashierHeader);
        PosShiftResponse resp = shiftService.getCurrentShiftForCashier(cashierHeader);
        return ResponseEntity.ok(APIResponse.<PosShiftResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Current shift retrieved")
                .data(resp)
                .build());
    }

    @GetMapping("/{shiftUuid}")
    @Operation(summary = "Get shift details")
    public ResponseEntity<APIResponse<PosShiftResponse>> getShift(@PathVariable String shiftUuid) {
        PosShiftResponse resp = shiftService.getShiftByUuid(shiftUuid);
        return ResponseEntity.ok(APIResponse.<PosShiftResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Shift retrieved")
                .data(resp)
                .build());
    }

    @GetMapping("/{shiftUuid}/summary")
    @Operation(summary = "Get shift sales summary")
    public ResponseEntity<APIResponse<PosShiftSummaryResponse>> getSummary(@PathVariable String shiftUuid) {
        PosShiftSummaryResponse resp = shiftService.getShiftSummary(shiftUuid);
        return ResponseEntity.ok(APIResponse.<PosShiftSummaryResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Shift summary retrieved")
                .data(resp)
                .build());
    }
}

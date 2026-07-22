package org.psint.beyosclothing.modules.pos.controller;

import lombok.RequiredArgsConstructor;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.pos.service.PosSearchAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/pos/analytics")
@RequiredArgsConstructor
public class PosSearchAnalyticsController {

    private final PosSearchAnalyticsService analyticsService;

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<APIResponse<Map<String, Long>>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int topN
    ) {
        Map<String, Long> data = analyticsService.topSearchedProducts(from, to, topN);
        return ResponseEntity.ok(APIResponse.<Map<String, Long>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Top products")
                .data(data)
                .build());
    }

    @GetMapping("/zero-searches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<APIResponse<List<String>>> zeroSearches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<String> data = analyticsService.zeroResultSearches(from, to);
        return ResponseEntity.ok(APIResponse.<List<String>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Zero-result searches")
                .data(data)
                .build());
    }

    @GetMapping("/conversion-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<APIResponse<Double>> conversionRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        double rate = analyticsService.searchToConversionRate(from, to);
        return ResponseEntity.ok(APIResponse.<Double>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Search-to-conversion rate")
                .data(rate)
                .build());
    }
}

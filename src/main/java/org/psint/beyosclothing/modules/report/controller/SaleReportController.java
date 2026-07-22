package org.psint.beyosclothing.modules.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.report.dto.request.SaleReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportResponse;
import org.psint.beyosclothing.modules.report.service.SaleReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin sales-report endpoints for the sales report page.
 */
@RestController
@RequestMapping({ "/api/v1/admin/reports/sales"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Sale Reports", description = "Admin sale reporting APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class SaleReportController {

    private final SaleReportService saleReportService;

    @GetMapping
    @Operation(summary = "Sales report", description = "Combined sales report payload for the admin sales report page")
    public ResponseEntity<APIResponse<SaleReportResponse>> getReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        SaleReportFilterRequest request = buildFilter(null, startDate, endDate, null, startTime, endTime, null);
        SaleReportResponse data = saleReportService.getReport(request);
        return ResponseEntity.ok(APIResponse.success("Sales report fetched successfully", data));
    }

    @PostMapping("/export")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Export sales report", description = "Export the sales report as a PDF file")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        SaleReportFilterRequest request = buildFilter(null, startDate, endDate, null, startTime, endTime, null);
        SaleReportExportResponse export = saleReportService.exportReport(request);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.getFileName()).build().toString())
                .body(export.getContent());
    }

    private SaleReportFilterRequest buildFilter(
            String period,
            String startDate,
            String endDate,
            String month,
            String startTime,
            String endTime,
            String format) {

        return SaleReportFilterRequest.builder()
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .month(month)
                .startTime(startTime)
                .endTime(endTime)
                .format(format)
                .build();
    }
}

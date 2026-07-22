package org.psint.beyosclothing.modules.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.report.dto.request.ItemReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.request.ItemSalesExportRequest;
import org.psint.beyosclothing.modules.report.dto.response.ItemChartPointResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemReportSummaryResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailPageResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.VariableProductDetailResponse;
import org.psint.beyosclothing.modules.report.service.ItemReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin item-report endpoints: summary metrics, chart series, paginated sales
 * details, and the variation breakdown for a variable product.
 */
@RestController
@RequestMapping("/api/v1/admin/reports/items")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Item Reports", description = "Admin reporting APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class ItemReportController {

    private final ItemReportService itemReportService;

    @GetMapping("/summary")
    @Operation(summary = "Item report summary", description = "Top metric cards for the item report page")
    public ResponseEntity<APIResponse<ItemReportSummaryResponse>> getSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String endTime) {

        ItemReportFilterRequest filter = ItemReportFilterRequest.builder()
                .startDate(startDate)
                .startTime(startTime)
                .endDate(endDate)
                .endTime(endTime)
                .build();

        ItemReportSummaryResponse data = itemReportService.getSummary(filter);
        return ResponseEntity.ok(APIResponse.success("Item report summary fetched successfully", data));
    }

    @GetMapping("/chart")
    @Operation(summary = "Item chart data", description = "Revenue and units series for the item chart")
    public ResponseEntity<APIResponse<List<ItemChartPointResponse>>> getChart(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String endTime) {

        ItemReportFilterRequest filter = ItemReportFilterRequest.builder()
                .startDate(startDate)
                .startTime(startTime)
                .endDate(endDate)
                .endTime(endTime)
                .build();

        List<ItemChartPointResponse> data = itemReportService.getChart(filter);
        return ResponseEntity.ok(APIResponse.success("Item chart data fetched successfully", data));
    }

    @GetMapping("/sales-details")
    @Operation(summary = "Item sales details", description = "Paginated per-product sales rows")
    public ResponseEntity<APIResponse<ItemSalesDetailPageResponse>> getSalesDetails(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String categoryId) {

        ItemReportFilterRequest filter = ItemReportFilterRequest.builder()
                .startDate(startDate)
                .startTime(startTime)
                .endDate(endDate)
                .endTime(endTime)
                .page(page)
                .limit(limit)
                .search(search)
                .type(type)
                .categoryId(categoryId)
                .build();

        ItemSalesDetailPageResponse data = itemReportService.getSalesDetails(filter);
        return ResponseEntity.ok(APIResponse.success("Item sales details fetched successfully", data));
    }

    @PostMapping("/export")
    @Operation(summary = "Export item sales details", description = "Generate and download a PDF of all item sales rows matching the filters")
    public ResponseEntity<byte[]> exportSalesDetails(@RequestBody ItemSalesExportRequest request) {
        SaleReportExportResponse export = itemReportService.exportSalesDetails(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.getFileName()).build().toString())
                .body(export.getContent());
    }

    @GetMapping("/products/{productId}/variations")
    @Operation(summary = "Variable product details", description = "Variation breakdown for a variable product")
    public ResponseEntity<APIResponse<VariableProductDetailResponse>> getProductVariations(
            @PathVariable Long productId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String endTime) {

        ItemReportFilterRequest filter = ItemReportFilterRequest.builder()
                .startDate(startDate)
                .startTime(startTime)
                .endDate(endDate)
                .endTime(endTime)
                .build();

        VariableProductDetailResponse data = itemReportService.getProductVariations(productId, filter);
        return ResponseEntity.ok(APIResponse.success("Variable product details fetched successfully", data));
    }
}

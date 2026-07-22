package org.psint.beyosclothing.modules.report.service;

import org.psint.beyosclothing.modules.report.dto.request.ItemReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.request.ItemSalesExportRequest;
import org.psint.beyosclothing.modules.report.dto.response.ItemChartPointResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemReportSummaryResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailPageResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.VariableProductDetailResponse;

import java.util.List;

public interface ItemReportService {

    /** Top metric cards for the item report window. */
    ItemReportSummaryResponse getSummary(ItemReportFilterRequest filter);

    /** Chart-ready revenue/units series for the item report window. */
    List<ItemChartPointResponse> getChart(ItemReportFilterRequest filter);

    /** Paginated per-product sales rows for the Sales Details table. */
    ItemSalesDetailPageResponse getSalesDetails(ItemReportFilterRequest filter);

    /** Variation breakdown for a single VARIABLE product. */
    VariableProductDetailResponse getProductVariations(Long productId, ItemReportFilterRequest filter);

    /** Generate a PDF export of all item sales rows matching the given filters. */
    SaleReportExportResponse exportSalesDetails(ItemSalesExportRequest request);
}

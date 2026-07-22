package org.psint.beyosclothing.modules.report.service;

import org.psint.beyosclothing.modules.report.dto.request.SaleReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportResponse;

public interface SaleReportService {

    /** Combined sales report payload for the admin sales report page. */
    SaleReportResponse getReport(SaleReportFilterRequest filter);

    /** Export the sales report as a downloadable file. */
    SaleReportExportResponse exportReport(SaleReportFilterRequest filter);
}

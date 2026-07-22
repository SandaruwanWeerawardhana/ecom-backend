package org.psint.beyosclothing.modules.report.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl.ChartPoint;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl.OrderSummaryResult;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl.PosSummaryResult;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl.ProductSalesResult;
import org.psint.beyosclothing.modules.report.consumer.ReportLookupServiceImpl.VariationSalesResult;
import org.psint.beyosclothing.modules.report.dto.request.ItemReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.request.ItemSalesExportRequest;
import org.psint.beyosclothing.modules.report.dto.external.ProductLookupInfo;
import org.psint.beyosclothing.modules.report.dto.external.VariantLookupInfo;
import org.psint.beyosclothing.modules.report.dto.response.ItemChartPointResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemReportSummaryResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailPageResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailResponse;
import org.psint.beyosclothing.modules.report.dto.response.ReportPaginationResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.VariationDetailResponse;
import org.psint.beyosclothing.modules.report.dto.response.VariableProductDetailResponse;
import org.psint.beyosclothing.modules.report.service.ItemReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Item report read model. All cross-module data is fetched via RabbitMQ through
 * {@link ReportLookupServiceImpl} — no direct cross-module entity access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemReportServiceImpl implements ItemReportService {

    // PDF theme colours (RGB 0-1) aligned with the BEYOS app UI: navy headers, orange accents.
    private static final String THEME_ORANGE = "0.95 0.42 0.13";       // #F26A21 brand accent
    private static final String THEME_ORANGE_TINT = "0.99 0.93 0.89";  // pale orange highlight
    private static final String THEME_TEXT = "0.13 0.16 0.22";         // default cell text

    private final ReportLookupServiceImpl reportLookupService;

    /**
     * Each summary metric is the order-side value plus the POS-side value for the window:
     * <ul>
     *   <li>totalRevenue ← SUM(orders.total) + SUM(pos_carts.subtotal)</li>
     *   <li>completedRevenue ← SUM(DELIVERED/COMPLETED orders.total) + SUM(pos_carts.subtotal)</li>
     *   <li>totalOrders ← COUNT(orders) + COUNT(pos_carts)</li>
     *   <li>itemsSold ← SUM(order_items.quantity) + SUM(pos_cart_items.quantity)</li>
     *   <li>itemValue ← SUM(order_items.total_price) + SUM(pos_cart_items.total_price)</li>
     * </ul>
     * Order data comes from the order module and POS data from the POS module, both via RabbitMQ.
     */
    @Override
    public ItemReportSummaryResponse getSummary(ItemReportFilterRequest filter) {
        ReportWindow window = resolveWindow(filter);

        OrderSummaryResult orderSummary = reportLookupService.lookupOrderSummary(window.start(), window.end());
        PosSummaryResult posSummary = reportLookupService.lookupPosSummary(window.start(), window.end());

        return ItemReportSummaryResponse.builder()
                .totalRevenue(orderSummary.totalRevenue().add(posSummary.totalRevenue()))
                .completedRevenue(orderSummary.completedRevenue().add(posSummary.totalRevenue()))
                .totalOrders(BigDecimal.valueOf(orderSummary.totalOrders() + posSummary.totalOrders()))
                .itemsSold(BigDecimal.valueOf(orderSummary.itemsSold() + posSummary.itemsSold()))
                .itemValue(orderSummary.itemValue().add(posSummary.itemValue()))
                .build();
    }

    /**
     * Always aggregates per created date (one point per day in the window). Each point holds
     * that day's combined revenue and items sold from both sources, merged by date:
     * order data (order_items, by created_at) plus POS data (completed pos_carts, by created_at).
     */
    @Override
    public List<ItemChartPointResponse> getChart(ItemReportFilterRequest filter) {
        ReportWindow window = resolveWindow(filter);

        // singleDay=false → daily buckets keyed by created date for every window size.
        List<ChartPoint> orderPoints = reportLookupService.lookupOrderChart(
                window.start(), window.end(), false);
        List<ChartPoint> posPoints = reportLookupService.lookupPosChart(
                window.start(), window.end(), false);

        return mergeChartPoints(orderPoints, posPoints).stream()
                .map(p -> ItemChartPointResponse.builder()
                        .date(p.date())
                        .label(p.label())
                        .revenue(p.revenue())
                        .itemsSold(p.itemsSold())
                        .build())
                .toList();
    }

    /**
     * Combine the order and POS daily series into one point per date. Both series are already
     * filled across the same window, so dates line up; same-date points add their revenue and
     * items sold while keeping the shared date/label.
     */
    private List<ChartPoint> mergeChartPoints(List<ChartPoint> orderPoints, List<ChartPoint> posPoints) {
        Map<String, ChartPoint> merged = new LinkedHashMap<>();
        for (ChartPoint point : orderPoints) {
            merged.put(point.date(), point);
        }
        for (ChartPoint point : posPoints) {
            merged.merge(point.date(), point, (existing, incoming) -> new ChartPoint(
                    existing.date(),
                    existing.label(),
                    existing.revenue().add(incoming.revenue()),
                    existing.itemsSold() + incoming.itemsSold()));
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public ItemSalesDetailPageResponse getSalesDetails(ItemReportFilterRequest filter) {
        ReportWindow window = resolveWindow(filter);
        int page = resolvePage(filter.getPage());
        int limit = resolveLimit(filter.getLimit());
        String search = normalize(filter.getSearch());

        // Order rows are pre-filtered by name in-DB; POS rows carry no title, so the name
        // filter is applied here once products are resolved, then both sources are merged.
        List<ProductSalesResult> orderSales = reportLookupService.lookupProductSales(
                window.start(), window.end(), search);
        List<ProductSalesResult> posSales = reportLookupService.lookupPosProductSales(
                window.start(), window.end());

        List<Long> productIds = Stream.concat(orderSales.stream(), posSales.stream())
                .map(ProductSalesResult::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductLookupInfo> products = reportLookupService.lookupProducts(productIds);

        List<ProductSalesResult> sales = mergeProductSales(orderSales, posSales, products, search);

        List<ItemSalesDetailResponse> rows = new ArrayList<>();
        for (ProductSalesResult sale : sales) {
            ProductLookupInfo product = products.get(sale.productId());
            String type = product != null && product.type() != null ? product.type() : "SIMPLE";
            if (!matchesType(type, filter.getType()) || !matchesCategory(product, filter.getCategoryId())) {
                continue;
            }

            List<VariationDetailResponse> variableDetails = "VARIABLE".equalsIgnoreCase(type)
                    ? buildVariationDetails(sale.productId(), window)
                    : List.of();

            rows.add(ItemSalesDetailResponse.builder()
                    .date(sale.date())
                    .time(sale.time())
                    .productId(sale.productId())
                    .productName(resolveProductName(sale, product))
                    .category(product != null ? product.category() : null)
                    .image(product != null ? product.image() : null)
                    .type(type)
                    .unitPrice(formatUnitPrice(sale.minUnitPrice(), sale.maxUnitPrice()))
                    .unitsSold(sale.unitsSold())
                    .totalRevenue(formatCurrency(sale.totalRevenue()))
                    .variableDetails(variableDetails)
                    .build());
        }

        int total = rows.size();
        int fromIndex = Math.min((page - 1) * limit, total);
        int toIndex = Math.min(fromIndex + limit, total);
        List<ItemSalesDetailResponse> pageRows = rows.subList(fromIndex, toIndex);

        return ItemSalesDetailPageResponse.builder()
                .salesDetails(pageRows)
                .pagination(ReportPaginationResponse.builder()
                        .page(page)
                        .limit(limit)
                        .total(total)
                        .totalPages((int) Math.ceil((double) total / limit))
                        .build())
                .build();
    }

    @Override
    public VariableProductDetailResponse getProductVariations(Long productId, ItemReportFilterRequest filter) {
        ReportWindow window = resolveWindow(filter);
        ProductLookupInfo product = productId != null
                ? reportLookupService.lookupProducts(List.of(productId)).get(productId)
                : null;

        return VariableProductDetailResponse.builder()
                .productId(productId)
                .productName(product != null ? product.title() : null)
                .type(product != null && product.type() != null ? product.type() : "VARIABLE")
                .variations(buildVariationDetails(productId, window))
                .build();
    }

    @Override
    public SaleReportExportResponse exportSalesDetails(ItemSalesExportRequest request) {
        ItemReportFilterRequest filter = ItemReportFilterRequest.builder()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .search(request.getSearch())
                .type(request.getType())
                .categoryId(request.getCategoryId())
                .page(1)
                .limit(1000)
                .build();

        ItemSalesDetailPageResponse page = getSalesDetails(filter);
        List<ItemSalesDetailResponse> rows = page != null && page.getSalesDetails() != null
                ? page.getSalesDetails()
                : List.of();

        String startDate = normalize(request.getStartDate());
        String endDate = normalize(request.getEndDate());
        String fileName = "item-sales-report-"
                + (startDate != null ? startDate : "all")
                + "-to-"
                + (endDate != null ? endDate : "all")
                + ".pdf";

        return SaleReportExportResponse.builder()
                .fileName(fileName)
                .contentType("application/pdf")
                .content(buildItemSalesPdf(rows, request))
                .build();
    }

    private byte[] buildItemSalesPdf(List<ItemSalesDetailResponse> rows, ItemSalesExportRequest request) {
        String contentStream = buildItemSalesPdfContentStream(rows, request);
        byte[] contentBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 841.9 595.3] "
                        + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + contentBytes.length + " >>\nstream\n" + contentStream + "\nendstream"
        );

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n")
                    .append(objects.get(i)).append("\n")
                    .append("endobj\n");
        }

        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xrefOffset).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }


    private String buildItemSalesPdfContentStream(
            List<ItemSalesDetailResponse> rows, ItemSalesExportRequest request) {

        final float PAGE_W  = 841.9f;
        final float PAGE_H  = 595.3f;
        final float MARGIN  = 30f;

        final Object[][] COLS = {
                { "Product",    MARGIN,        170f },
                { "Category",   MARGIN + 175f,  70f },
                { "Type",       MARGIN + 250f,  60f },
                { "Unit Price", MARGIN + 315f,  70f },
                { "Qty",        MARGIN + 390f,  40f },
                { "Revenue",    MARGIN + 435f,  75f },
                { "Date",       MARGIN + 515f,  75f },
                { "Time",       MARGIN + 595f,  50f },
        };
        final float TABLE_RIGHT = MARGIN + 645f;  // = last col x + width

        // ── Row geometry ──────────────────────────────────────────────────────
        final float HEADER_ROW_H = 18f;
        final float DATA_ROW_H   = 14f;
        final float VAR_ROW_H    = 12f;

        float y = PAGE_H - MARGIN;

        // ── Collect everything into a raw-operator StringBuilder ─────────────
        StringBuilder sb = new StringBuilder();

        // ── 1. BACKGROUND — full page white ──────────────────────────────────
        sb.append("1 1 1 rg\n");
        sb.append("0 0 ").append(fmt(PAGE_W)).append(' ').append(fmt(PAGE_H)).append(" re f\n");

        // 2. TITLE BLOCK

        appendRect(sb, MARGIN, y - 42f, 4f, 42f, THEME_ORANGE);
        float titleX = MARGIN + 12f;

        sb.append("BT\n/F1 16 Tf\n0.12 0.23 0.37 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 18f)).append(" Tm\n");
        sb.append("(Item Sales Details Report) Tj\n");
        sb.append("ET\n");

        String startDate = request.getStartDate() != null ? request.getStartDate() : "All";
        String endDate   = request.getEndDate()   != null ? request.getEndDate()   : "All";
        sb.append("BT\n/F2 9 Tf\n0.22 0.27 0.36 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 31f)).append(" Tm\n");
        sb.append("(Date Range: ").append(escapePdf(startDate))
                .append(" to ").append(escapePdf(endDate)).append(") Tj\n");
        sb.append("ET\n");

        sb.append("BT\n/F2 9 Tf\n0.22 0.27 0.36 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 42f)).append(" Tm\n");
        appendFilterLine(sb, request);
        sb.append("ET\n");

        y -= 52f;

        // 3. SUMMARY PILL BADGES  (Total Products | Total Units Sold)
        long totalUnits = rows.stream()
                .mapToLong(r -> r.getUnitsSold() != null ? r.getUnitsSold() : 0).sum();

        float badgeY    = y - 20f;
        float badgeH    = 20f;
        float badge1X   = MARGIN;
        float badge2X   = MARGIN + 160f;

        // Badge 1 background
        appendRoundRect(sb, badge1X, badgeY, 145f, badgeH, THEME_ORANGE_TINT);
        // Badge 2 background
        appendRoundRect(sb, badge2X, badgeY, 160f, badgeH, THEME_ORANGE_TINT);

        sb.append("BT\n/F1 8 Tf\n0.12 0.23 0.37 rg\n");
        sb.append("1 0 0 1 ").append(fmt(badge1X + 8f)).append(' ').append(fmt(badgeY + 6f)).append(" Tm\n");
        sb.append("(Total Products: ").append(rows.size()).append(") Tj\n");
        sb.append("ET\n");

        sb.append("BT\n/F1 8 Tf\n0.12 0.23 0.37 rg\n");
        sb.append("1 0 0 1 ").append(fmt(badge2X + 8f)).append(' ').append(fmt(badgeY + 6f)).append(" Tm\n");
        sb.append("(Total Units Sold: ").append(totalUnits).append(") Tj\n");
        sb.append("ET\n");

        y = badgeY - 10f;   // gap below badges

        // 4. TABLE HEADER ROW
        float headerY = y - HEADER_ROW_H;

        // Header background — navy
        appendRect(sb, MARGIN, headerY, TABLE_RIGHT - MARGIN, HEADER_ROW_H, "0.12 0.23 0.37");

        // Header labels — white, bold (F1), one BT/ET per cell for absolute positioning
        for (Object[] col : COLS) {
            String label      = (String) col[0];
            float  cx         = (float)  col[1];
            float  cw         = (float)  col[2];
            boolean rightAlign = label.equals("Unit Price") || label.equals("Qty") || label.equals("Revenue");
            float tx = rightAlign ? cx + cw - 4f - textWidth(label, 8) : cx + 4f;
            sb.append("BT\n/F1 8 Tf\n1 1 1 rg\n");
            sb.append("1 0 0 1 ").append(fmt(tx)).append(' ').append(fmt(headerY + 5f)).append(" Tm\n");
            sb.append('(').append(escapePdf(label)).append(") Tj\n");
            sb.append("ET\n");
        }

        y = headerY;   // table rows start below header

        // ─────────────────────────────────────────────────────────────────────
        // 5. DATA ROWS
        // ─────────────────────────────────────────────────────────────────────
        int rowIndex = 0;
        for (ItemSalesDetailResponse row : rows) {

            float rowY = y - DATA_ROW_H;

            // Alternating row backgrounds
            if (rowIndex % 2 == 0) {
                appendRect(sb, MARGIN, rowY, TABLE_RIGHT - MARGIN, DATA_ROW_H, "0.94 0.96 0.98");  // #F0F4FA
            } else {
                appendRect(sb, MARGIN, rowY, TABLE_RIGHT - MARGIN, DATA_ROW_H, "1 1 1");           // white
            }

            // Bottom border line (light grey)
            appendHLine(sb, MARGIN, rowY, TABLE_RIGHT - MARGIN, "0.80 0.84 0.89");

            // Column order: Product | Category | Type | Unit Price | Qty | Revenue | Date | Time
            String[][] cells = {
                    { truncate(row.getProductName(), 24), "left"  },
                    { truncate(row.getCategory(),    10), "left"  },
                    { truncate(row.getType(),         8), "left"  },
                    { orEmpty(row.getUnitPrice()),         "right" },
                    { row.getUnitsSold() != null ? String.valueOf(row.getUnitsSold()) : "0", "right" },
                    { orEmpty(row.getTotalRevenue()),      "right" },
                    { orEmpty(row.getDate()),              "left"  },
                    { orEmpty(row.getTime()),              "left"  },
            };

            // One BT/ET per cell so Tm positions are always absolute
            for (int c = 0; c < COLS.length; c++) {
                float cx  = (float) COLS[c][1];
                float cw  = (float) COLS[c][2];
                boolean ra = cells[c][1].equals("right");
                float tx   = ra ? cx + cw - 4f - textWidth(cells[c][0], 8) : cx + 4f;
                // Revenue column (index 5) uses the orange accent to echo the UI figures.
                String cellColor = c == 5 ? THEME_ORANGE : THEME_TEXT;
                sb.append("BT\n/F2 8 Tf\n").append(cellColor).append(" rg\n");
                sb.append("1 0 0 1 ").append(fmt(tx)).append(' ').append(fmt(rowY + 4f)).append(" Tm\n");
                sb.append('(').append(escapePdf(cells[c][0])).append(") Tj\n");
                sb.append("ET\n");
            }

            y = rowY;
            rowIndex++;

            // ── VARIATION SUB-ROWS ───────────────────────────────────────────
            if (row.getVariableDetails() != null && !row.getVariableDetails().isEmpty()) {
                for (var variation : row.getVariableDetails()) {

                    float varY = y - VAR_ROW_H;

                    // Pale-orange background for sub-rows
                    appendRect(sb, MARGIN, varY, TABLE_RIGHT - MARGIN, VAR_ROW_H, THEME_ORANGE_TINT);

                    // Left indent accent
                    appendRect(sb, MARGIN, varY, 3f, VAR_ROW_H, THEME_ORANGE);

                    appendHLine(sb, MARGIN, varY, TABLE_RIGHT - MARGIN, "0.80 0.84 0.89");

                    // Build variation label for Product column
                    String varLabel = "  > "
                            + "SKU:" + (variation.getSku()   != null ? variation.getSku()   : "-")
                            + "  Size:"  + (variation.getSize()  != null ? variation.getSize()  : "-")
                            + "  Color:" + (variation.getColor() != null ? variation.getColor() : "-");

                    // Product label
                    sb.append("BT\n/F2 7 Tf\n0.25 0.35 0.50 rg\n");
                    sb.append("1 0 0 1 ").append(fmt((float) COLS[0][1] + 7f)).append(' ').append(fmt(varY + 3f)).append(" Tm\n");
                    sb.append('(').append(escapePdf(truncate(varLabel, 32))).append(") Tj\n");
                    sb.append("ET\n");

                    // Unit Price
                    float cxUP = (float) COLS[3][1], cwUP = (float) COLS[3][2];
                    String upVal = orEmpty(variation.getUnitPrice());
                    sb.append("BT\n/F2 7 Tf\n0.25 0.35 0.50 rg\n");
                    sb.append("1 0 0 1 ").append(fmt(cxUP + cwUP - 4f - textWidth(upVal, 7))).append(' ').append(fmt(varY + 3f)).append(" Tm\n");
                    sb.append('(').append(escapePdf(upVal)).append(") Tj\n");
                    sb.append("ET\n");

                    // Qty
                    float cxQty = (float) COLS[4][1], cwQty = (float) COLS[4][2];
                    String qtyVal = String.valueOf(variation.getSold());
                    sb.append("BT\n/F2 7 Tf\n0.25 0.35 0.50 rg\n");
                    sb.append("1 0 0 1 ").append(fmt(cxQty + cwQty - 4f - textWidth(qtyVal, 7))).append(' ').append(fmt(varY + 3f)).append(" Tm\n");
                    sb.append('(').append(escapePdf(qtyVal)).append(") Tj\n");
                    sb.append("ET\n");

                    // Revenue
                    float cxRev = (float) COLS[5][1], cwRev = (float) COLS[5][2];
                    String revVal = orEmpty(variation.getTotalRevenue());
                    sb.append("BT\n/F2 7 Tf\n0.25 0.35 0.50 rg\n");
                    sb.append("1 0 0 1 ").append(fmt(cxRev + cwRev - 4f - textWidth(revVal, 7))).append(' ').append(fmt(varY + 3f)).append(" Tm\n");
                    sb.append('(').append(escapePdf(revVal)).append(") Tj\n");
                    sb.append("ET\n");

                    y = varY;
                }
            }
        }

        // ── Bottom border of the whole table ─────────────────────────────────
        appendHLine(sb, MARGIN, y, TABLE_RIGHT - MARGIN, "0.12 0.23 0.37");

        // ── Column dividers (vertical lines through the header + all rows) ───
        sb.append("0.80 0.84 0.89 RG\n0.4 w\n");
        for (int c = 1; c < COLS.length; c++) {
            float lx = (float) COLS[c][1] - 1f;
            sb.append(fmt(lx)).append(' ').append(fmt(y)).append(" m\n");
            sb.append(fmt(lx)).append(' ').append(fmt(headerY + HEADER_ROW_H)).append(" l S\n");
        }

        // ── Footer bar ───────────────────────────────────────────────────────
        appendRect(sb, 0, 0, PAGE_W, 18f, "0.12 0.23 0.37");
        sb.append("BT\n/F2 7 Tf\n1 1 1 rg\n");
        sb.append("1 0 0 1 ").append(fmt(MARGIN)).append(" 5 Tm\n");
        sb.append("(Generated by Item Sales Report System) Tj\n");
        sb.append("ET\n");

        return sb.toString();
    }

// ─────────────────────────────────────────────────────────────────────────────
// PRIVATE DRAWING HELPERS  — add these alongside your existing helpers
// (escapePdf, pad, padLeft, appendFilterLine must already exist in your class)
// ─────────────────────────────────────────────────────────────────────────────

    /** Append a filled rectangle: x y w h, colour = "r g b" string (0-1). */
    private void appendRect(StringBuilder sb,
                            float x, float y, float w, float h,
                            String rgbFill) {
        sb.append(rgbFill).append(" rg\n");
        sb.append(fmt(x)).append(' ').append(fmt(y)).append(' ')
                .append(fmt(w)).append(' ').append(fmt(h)).append(" re f\n");
    }

    /**
     * Approximate round-rect via a slightly inset rect (PDF 1.4 content streams
     * don't have a native round-rect operator, so we fake it with a plain rect).
     */
    private void appendRoundRect(StringBuilder sb,
                                 float x, float y, float w, float h,
                                 String rgbFill) {
        appendRect(sb, x, y, w, h, rgbFill);
    }

    /** Append a 0.4-pt horizontal line. */
    private void appendHLine(StringBuilder sb,
                             float x, float y, float w,
                             String rgbStroke) {
        sb.append(rgbStroke).append(" RG\n0.4 w\n");
        sb.append(fmt(x)).append(' ').append(fmt(y)).append(" m\n");
        sb.append(fmt(x + w)).append(' ').append(fmt(y)).append(" l S\n");
    }

    /** Format a float to 2 dp, strip trailing zeros. */
    private String fmt(float v) {
        String s = String.format("%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * Very rough character-width estimate for Helvetica at a given font size.
     * Accurate enough to right-align numeric columns without a font-metrics table.
     */
    private float textWidth(String text, int fontSize) {
        if (text == null || text.isEmpty()) return 0f;
        return text.length() * fontSize * 0.52f;
    }

    /** Truncate a string to maxLen characters, appending "…" if needed. */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "~";
    }

    /** Return empty string if value is null. */
    private String orEmpty(String s) {
        return s != null ? s : "";
    }

    private String escapePdf(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private void appendFilterLine(StringBuilder sb, ItemSalesExportRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getSearch() != null && !request.getSearch().isBlank()) {
            parts.add("Search: " + request.getSearch().trim());
        }
        if (request.getType() != null && !request.getType().isBlank()) {
            parts.add("Type: " + request.getType().trim());
        }
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            parts.add("Category ID: " + request.getCategoryId().trim());
        }
        String filterLine = parts.isEmpty() ? "Filters: None" : "Filters: " + String.join("   ", parts);
        sb.append("(").append(escapePdf(filterLine)).append(") Tj\n");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private List<VariationDetailResponse> buildVariationDetails(
            Long productId,
            ReportWindow window) {

        List<VariationSalesResult> orderVariationSales = reportLookupService.lookupVariationSales(
                productId, window.start(), window.end());
        List<VariationSalesResult> posVariationSales = reportLookupService.lookupPosVariationSales(
                productId, window.start(), window.end());
        List<VariationSalesResult> variationSales = mergeVariationSales(orderVariationSales, posVariationSales);

        Map<Long, VariantLookupInfo> variants = reportLookupService.lookupVariants(
                productId,
                variationSales.stream().map(VariationSalesResult::variantId).filter(Objects::nonNull).toList());

        return variationSales.stream()
                .filter(v -> v.variantId() != null)
                .sorted(Comparator.comparingLong(VariationSalesResult::sold).reversed())
                .map(sale -> {
                    VariantLookupInfo variant = variants.get(sale.variantId());
                    Map<String, String> attributes = parseAttributes(
                            variant != null ? variant.attributeSummary() : sale.variantTitle());
                    return VariationDetailResponse.builder()
                            .variationId(true ? sale.variantId() : null)
                            .sku(variant != null ? variant.sku() : null)
                            .color(attributes.get("color"))
                            .size(attributes.get("size"))
                            .unitPrice(formatUnitPrice(sale.minUnitPrice(), sale.maxUnitPrice()))
                            .stock(reportLookupService.lookupStock(productId, sale.variantId()))
                            .sold(sale.sold())
                            .totalRevenue(true ? formatCurrency(sale.totalRevenue()) : null)
                            .build();
                })
                .toList();
    }

    /**
     * Merge order and POS product-sales rows by product id. Order rows are already name-filtered
     * in-DB; a POS row joins the result when its product already matched (present from the order
     * side) or its resolved title matches the search. Matching rows have their units, revenue and
     * price range combined and keep the most recent sale date/time.
     */
    private List<ProductSalesResult> mergeProductSales(
            List<ProductSalesResult> orderSales,
            List<ProductSalesResult> posSales,
            Map<Long, ProductLookupInfo> products,
            String search) {

        Map<Long, ProductSalesResult> merged = new LinkedHashMap<>();
        for (ProductSalesResult sale : orderSales) {
            if (sale.productId() != null) {
                merged.merge(sale.productId(), sale, this::combineProductSales);
            }
        }
        for (ProductSalesResult sale : posSales) {
            Long productId = sale.productId();
            if (productId == null) {
                continue;
            }
            if (merged.containsKey(productId) || matchesSearch(products.get(productId), search)) {
                merged.merge(productId, sale, this::combineProductSales);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private ProductSalesResult combineProductSales(ProductSalesResult a, ProductSalesResult b) {
        ProductSalesResult later = saleKey(b).compareTo(saleKey(a)) > 0 ? b : a;
        return new ProductSalesResult(
                a.productId(),
                later.date(),
                later.time(),
                a.unitsSold() + b.unitsSold(),
                safeAdd(a.totalRevenue(), b.totalRevenue()),
                safeMin(a.minUnitPrice(), b.minUnitPrice()),
                safeMax(a.maxUnitPrice(), b.maxUnitPrice()),
                a.productTitle() != null ? a.productTitle() : b.productTitle());
    }

    /** Sortable "date T time" key (ISO fields sort lexicographically); nulls sort earliest. */
    private String saleKey(ProductSalesResult sale) {
        return (sale.date() != null ? sale.date() : "") + "T" + (sale.time() != null ? sale.time() : "");
    }

    /** Merge order and POS variation-sales rows by variant id, combining units, revenue and price range. */
    private List<VariationSalesResult> mergeVariationSales(
            List<VariationSalesResult> orderSales, List<VariationSalesResult> posSales) {

        Map<Long, VariationSalesResult> merged = new LinkedHashMap<>();
        for (VariationSalesResult sale : orderSales) {
            if (sale.variantId() != null) {
                merged.merge(sale.variantId(), sale, this::combineVariationSales);
            }
        }
        for (VariationSalesResult sale : posSales) {
            if (sale.variantId() != null) {
                merged.merge(sale.variantId(), sale, this::combineVariationSales);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private VariationSalesResult combineVariationSales(VariationSalesResult a, VariationSalesResult b) {
        return new VariationSalesResult(
                a.variantId(),
                a.sold() + b.sold(),
                safeAdd(a.totalRevenue(), b.totalRevenue()),
                safeMin(a.minUnitPrice(), b.minUnitPrice()),
                safeMax(a.maxUnitPrice(), b.maxUnitPrice()),
                a.variantTitle() != null ? a.variantTitle() : b.variantTitle());
    }

    private boolean matchesSearch(ProductLookupInfo product, String search) {
        if (search == null) {
            return true;
        }
        if (product == null || product.title() == null) {
            return false;
        }
        return product.title().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    private BigDecimal safeMin(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.min(b);
    }

    private BigDecimal safeMax(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.max(b);
    }

    private String resolveProductName(ProductSalesResult sale, ProductLookupInfo product) {
        if (product != null && product.title() != null && !product.title().isBlank()) {
            return product.title();
        }
        return sale.productTitle();
    }

    private boolean matchesType(String actualType, String requestedType) {
        return requestedType == null || requestedType.isBlank()
                || actualType.equalsIgnoreCase(requestedType.trim());
    }

    private boolean matchesCategory(ProductLookupInfo product, String requestedCategoryId) {
        if (requestedCategoryId == null || requestedCategoryId.isBlank()) {
            return true;
        }
        if (product == null || product.categoryId() == null) {
            return false;
        }
        return requestedCategoryId.trim().equals(String.valueOf(product.categoryId()));
    }

    private int resolvePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 100);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String formatUnitPrice(BigDecimal min, BigDecimal max) {
        BigDecimal safeMin = min != null ? min : BigDecimal.ZERO;
        BigDecimal safeMax = max != null ? max : safeMin;
        if (safeMin.compareTo(safeMax) == 0) {
            return formatCurrency(safeMin);
        }
        return formatCurrency(safeMin) + " - " + formatCurrency(safeMax);
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("#,##0.##");
        return "Rs. " + formatter.format(safeAmount);
    }

    private Map<String, String> parseAttributes(String raw) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return attributes;
        }
        for (String part : raw.split(",")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            attributes.put(pair[0].trim().toLowerCase(Locale.ROOT), pair[1].trim());
        }
        return attributes;
    }

    private ReportWindow resolveWindow(ItemReportFilterRequest filter) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        LocalDate startDate = parseDate(filter.getStartDate(), "startDate", today);
        LocalDate endDate   = parseDate(filter.getEndDate(),   "endDate",   today);
        LocalTime startTime = parseTime(filter.getStartTime(), "startTime", LocalTime.MIN);
        LocalTime endTime = parseEndTime(filter.getEndTime());

        LocalDateTime start = LocalDateTime.of(startDate, startTime);
        LocalDateTime end = LocalDateTime.of(endDate, endTime);

        if (start.isAfter(end)) {
            throw new BadRequestException("startDate/startTime must not be after endDate/endTime");
        }

        return new ReportWindow(startDate, endDate, start, end);
    }

    private LocalDate parseDate(String raw, String field, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must be in yyyy-MM-dd format");
        }
    }

    private LocalTime parseTime(String raw, String field, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must be in HH:mm format");
        }
    }

    private LocalTime parseEndTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.MAX;
        }
        LocalTime parsed = parseTime(raw, "endTime", LocalTime.MAX);
        return parsed.plusMinutes(1).minusNanos(1);
    }

    private record ReportWindow(LocalDate startDate, LocalDate endDate, LocalDateTime start, LocalDateTime end) {
        boolean isSingleDay() {
            return startDate.equals(endDate);
        }
    }
}

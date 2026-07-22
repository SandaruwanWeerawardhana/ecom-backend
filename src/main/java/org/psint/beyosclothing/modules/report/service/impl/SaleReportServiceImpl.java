package org.psint.beyosclothing.modules.report.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.report.dto.request.ItemReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.request.SaleReportFilterRequest;
import org.psint.beyosclothing.modules.report.dto.response.ItemChartPointResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemReportSummaryResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailPageResponse;
import org.psint.beyosclothing.modules.report.dto.response.ItemSalesDetailResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportCategoryResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportChartsResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportDetailResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportExportResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportFilterResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportProductRevenueResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportSummaryResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportTopSalesResponse;
import org.psint.beyosclothing.modules.report.dto.response.SaleReportTrendResponse;
import org.psint.beyosclothing.modules.report.service.ItemReportService;
import org.psint.beyosclothing.modules.report.service.SaleReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleReportServiceImpl implements SaleReportService {

    private static final Pattern MONEY_PATTERN = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private static final String FORMAT_PDF = "pdf";
    private static final String PERIOD_DAILY = "daily";
    private static final String PERIOD_WEEKLY = "weekly";
    private static final String PERIOD_MONTHLY = "monthly";
    private static final String DEFAULT_START_TIME = "00:00";
    private static final String DEFAULT_END_TIME = "23:59";
    private static final Set<String> VALID_PERIODS = Set.of(PERIOD_DAILY, PERIOD_WEEKLY, PERIOD_MONTHLY);
    private static final int SALES_ROW_LIMIT = 100;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final String UNCATEGORIZED = "Uncategorized";

    // PDF theme colours (RGB 0-1) aligned with the BEYOS app UI: navy headers, orange accents.
    private static final String THEME_ORANGE = "0.95 0.42 0.13";       // #F26A21 brand accent
    private static final String THEME_ORANGE_TINT = "0.99 0.93 0.89";  // pale orange highlight
    private static final String THEME_TEXT = "0.13 0.16 0.22";         // default cell text

    private final ItemReportService itemReportService;

    @Override
    public SaleReportResponse getReport(SaleReportFilterRequest filter) {
        SaleReportFilterRequest resolved = resolveFilter(filter);
        ItemReportFilterRequest itemFilter = toItemFilter(resolved);

        ItemReportSummaryResponse summary = itemReportService.getSummary(itemFilter);
        List<ItemChartPointResponse> chart = itemReportService.getChart(itemFilter);
        ItemSalesDetailPageResponse salesPage = itemReportService.getSalesDetails(itemFilter);
        List<ItemSalesDetailResponse> itemDetails = salesPage != null && salesPage.getSalesDetails() != null
                ? salesPage.getSalesDetails()
                : List.of();
        List<SaleReportDetailResponse> rows = itemDetails.stream()
                .map(this::toSaleDetail)
                .toList();

        return SaleReportResponse.builder()
                .period(resolved.getPeriod())
                .filters(toFilterResponse(resolved))
                .summary(buildSummary(summary, chart, rows))
                .charts(SaleReportChartsResponse.builder()
                        .revenueTrend(buildTrend(chart))
                        .categoryRevenue(buildSalesByCategory(rows))
                        .productRevenue(buildTopProducts(rows))
                        .build())
                .rows(rows)
                .build();
    }

    @Override
    public SaleReportExportResponse exportReport(SaleReportFilterRequest filter) {
        SaleReportFilterRequest resolved = resolveFilter(filter);
        String format = normalize(resolved.getFormat(), FORMAT_PDF).toLowerCase(Locale.ROOT);
        if (!FORMAT_PDF.equals(format)) {
            throw new BadRequestException("format must be pdf");
        }

        SaleReportResponse report = getReport(resolved);
        return SaleReportExportResponse.builder()
                .fileName(buildFileName(resolved, format))
                .contentType("application/pdf")
                .content(buildPdf(report))
                .build();
    }

    private SaleReportSummaryResponse buildSummary(
            ItemReportSummaryResponse source,
            List<ItemChartPointResponse> chart,
            List<SaleReportDetailResponse> rows) {

        BigDecimal totalRevenue = orZero(source != null ? source.getCompletedRevenue() : null);
        BigDecimal expenses = orZero(source != null ? source.getTotalRevenue() : null);
        BigDecimal productsSold = orZero(source != null ? source.getItemsSold() : null);
        BigDecimal totalOrders = orZero(source != null ? source.getTotalOrders() : null);
        BigDecimal averageOrderValue = totalOrders.compareTo(BigDecimal.ZERO) > 0
                ? totalRevenue.divide(totalOrders, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal profitNet = expenses.subtract(totalRevenue).max(BigDecimal.ZERO);
        BigDecimal lossNet = totalRevenue.subtract(expenses).max(BigDecimal.ZERO);

        return SaleReportSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .productsSold(productsSold)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .expenses(expenses)
                .profitNet(profitNet)
                .lossNet(lossNet)
                .topSalesDay(buildTopSalesDay(chart, rows))
                .topSalesWeek(null)
                .build();
    }

    private SaleReportTopSalesResponse buildTopSalesDay(
            List<ItemChartPointResponse> chart,
            List<SaleReportDetailResponse> rows) {

        ItemChartPointResponse topPoint = chart == null ? null : chart.stream()
                .max(Comparator.comparing(p -> orZero(p.getRevenue())))
                .orElse(null);
        SaleReportDetailResponse topProduct = rows.stream()
                .max(Comparator.comparing(d -> orZero(d.getTotalRevenue())))
                .orElse(null);

        if (topPoint == null && topProduct == null) {
            return null;
        }
        return SaleReportTopSalesResponse.builder()
                .label(topPoint != null ? topPoint.getLabel() : null)
                .revenue(topPoint != null ? orZero(topPoint.getRevenue()) : orZero(topProduct != null ? topProduct.getTotalRevenue() : null))
                .product(topProduct != null ? topProduct.getProduct() : null)
                .build();
    }

    private List<SaleReportTrendResponse> buildTrend(List<ItemChartPointResponse> chart) {
        if (chart == null) {
            return List.of();
        }
        return chart.stream()
                .map(point -> SaleReportTrendResponse.builder()
                        .name(point.getLabel())
                        .revenue(orZero(point.getRevenue()))
                        .build())
                .toList();
    }

    private List<SaleReportCategoryResponse> buildSalesByCategory(List<SaleReportDetailResponse> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (SaleReportDetailResponse row : rows) {
            String category = row.getCategory() != null && !row.getCategory().isBlank()
                    ? row.getCategory()
                    : UNCATEGORIZED;
            totals.merge(category, orZero(row.getTotalRevenue()), BigDecimal::add);
        }

        // Only categories that actually earned revenue, largest share first.
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> SaleReportCategoryResponse.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private List<SaleReportProductRevenueResponse> buildTopProducts(List<SaleReportDetailResponse> rows) {
        // Highest-earning products first, dropping zero-revenue rows, capped to the top N.
        return rows.stream()
                .filter(row -> orZero(row.getTotalRevenue()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((SaleReportDetailResponse d) -> orZero(d.getTotalRevenue())).reversed())
                .limit(TOP_PRODUCTS_LIMIT)
                .map(row -> SaleReportProductRevenueResponse.builder()
                        .name(row.getProduct())
                        .revenue(orZero(row.getTotalRevenue()))
                        .build())
                .toList();
    }

    private SaleReportDetailResponse toSaleDetail(ItemSalesDetailResponse item) {
        BigDecimal totalRevenue = parseMoney(item.getTotalRevenue());
        BigDecimal price = parseMoney(item.getUnitPrice());
        return SaleReportDetailResponse.builder()
                .date(item.getDate())
                .day(resolveDay(item.getDate()))
                .time(item.getTime())
                .productUuid(item.getProductId() != null ? String.valueOf(item.getProductId()) : null)
                .product(item.getProductName())
                .category(item.getCategory())
                .price(price)
                .qty(item.getUnitsSold())
                .totalRevenue(totalRevenue)
                .build();
    }

    private SaleReportFilterRequest resolveFilter(SaleReportFilterRequest raw) {
        SaleReportFilterRequest filter = raw != null ? raw : SaleReportFilterRequest.builder().build();
        String period = normalize(filter.getPeriod(), null);

        LocalDate startDate;
        LocalDate endDate;

        if (period != null) {
            period = period.toLowerCase(Locale.ROOT);
            if (!VALID_PERIODS.contains(period)) {
                throw new BadRequestException("period must be daily, weekly, or monthly");
            }
            String month = normalize(filter.getMonth(), null);
            if (PERIOD_MONTHLY.equals(period)) {
                startDate = resolveMonthlyStartDate(month);
                endDate = parseMonth(month).atEndOfMonth();
            } else {
                startDate = resolveDailyWeeklyStartDate(filter);
                endDate = filter.getEndDate() == null || filter.getEndDate().isBlank()
                        ? resolveDefaultEndDate(period, startDate)
                        : parseDate(filter.getEndDate(), "endDate");
            }
        } else {
            if (filter.getStartDate() == null || filter.getStartDate().isBlank()) {
                throw new BadRequestException("startDate is required");
            }
            startDate = parseDate(filter.getStartDate(), "startDate");
            endDate = filter.getEndDate() == null || filter.getEndDate().isBlank()
                    ? startDate
                    : parseDate(filter.getEndDate(), "endDate");
        }

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }

        return SaleReportFilterRequest.builder()
                .period(period)
                .startDate(startDate.format(DATE_FORMAT))
                .endDate(endDate.format(DATE_FORMAT))
                .startTime(normalize(filter.getStartTime(), DEFAULT_START_TIME))
                .endTime(normalize(filter.getEndTime(), DEFAULT_END_TIME))
                .format(filter.getFormat())
                .build();
    }

    private LocalDate resolveMonthlyStartDate(String month) {
        if (month == null) {
            throw new BadRequestException("month is required for monthly period");
        }
        return parseMonth(month).atDay(1);
    }

    private LocalDate resolveDailyWeeklyStartDate(SaleReportFilterRequest filter) {
        if (filter.getStartDate() == null || filter.getStartDate().isBlank()) {
            throw new BadRequestException("startDate is required for daily and weekly periods");
        }
        return parseDate(filter.getStartDate(), "startDate");
    }

    private LocalDate resolveDefaultEndDate(String period, LocalDate startDate) {
        if (PERIOD_WEEKLY.equals(period)) {
            return startDate.plusDays(6);
        }
        return startDate;
    }

    private ItemReportFilterRequest toItemFilter(SaleReportFilterRequest filter) {
        return ItemReportFilterRequest.builder()
                .startDate(filter.getStartDate())
                .startTime(filter.getStartTime())
                .endDate(filter.getEndDate())
                .endTime(filter.getEndTime())
                .limit(SALES_ROW_LIMIT)
                .build();
    }

    private SaleReportFilterResponse toFilterResponse(SaleReportFilterRequest filter) {
        return SaleReportFilterResponse.builder()
                .startDate(filter.getStartDate())
                .endDate(filter.getEndDate())
                .month(filter.getMonth())
                .startTime(filter.getStartTime())
                .endTime(filter.getEndTime())
                .build();
    }

    private byte[] buildPdf(SaleReportResponse report) {
        String contentStream = buildPdfContentStream(report);
        byte[] contentBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = buildPdfObjects(contentBytes.length, contentStream);

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n")
                    .append(objects.get(i)).append("\n")
                    .append("endobj\n");
        }

        appendXrefTable(pdf, offsets, objects.size());
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private List<String> buildPdfObjects(int contentLength, String contentStream) {
        return List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 841.9 595.3] "
                        + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + contentLength + " >>\nstream\n" + contentStream + "\nendstream"
        );
    }

    private void appendXrefTable(StringBuilder pdf, List<Integer> offsets, int objectCount) {
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objectCount + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objectCount + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xrefOffset).append("\n%%EOF");
    }

    private String buildPdfContentStream(SaleReportResponse report) {
        final float PAGE_W = 841.9f;
        final float PAGE_H = 595.3f;
        final float MARGIN = 30f;

        // Column layout: label, x-position, width.
        final Object[][] COLS = {
                { "Date",     MARGIN,         75f },
                { "Day",      MARGIN + 78f,   45f },
                { "Time",     MARGIN + 126f,  50f },
                { "Product",  MARGIN + 179f,  180f },
                { "Category", MARGIN + 362f,  90f },
                { "Price",    MARGIN + 455f,  80f },
                { "Qty",      MARGIN + 538f,  45f },
                { "Revenue",  MARGIN + 586f,  95f },
        };
        final float TABLE_RIGHT = MARGIN + 684f;  // = last col x + width

        final float HEADER_ROW_H = 18f;
        final float DATA_ROW_H = 14f;

        float y = PAGE_H - MARGIN;
        StringBuilder sb = new StringBuilder();

        // 1. Full-page white background
        sb.append("1 1 1 rg\n");
        sb.append("0 0 ").append(fmt(PAGE_W)).append(' ').append(fmt(PAGE_H)).append(" re f\n");

        // 2. Title block
        appendRect(sb, MARGIN, y - 42f, 4f, 42f, THEME_ORANGE);
        float titleX = MARGIN + 12f;

        sb.append("BT\n/F1 16 Tf\n0.12 0.23 0.37 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 18f)).append(" Tm\n");
        sb.append("(Sales Report) Tj\n");
        sb.append("ET\n");

        SaleReportFilterResponse filters = report.getFilters();
        String startDate = filters != null && filters.getStartDate() != null ? filters.getStartDate() : "All";
        String endDate = filters != null && filters.getEndDate() != null ? filters.getEndDate() : "All";
        sb.append("BT\n/F2 9 Tf\n0.22 0.27 0.36 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 31f)).append(" Tm\n");
        sb.append("(Date Range: ").append(escapePdf(startDate))
                .append(" to ").append(escapePdf(endDate)).append(") Tj\n");
        sb.append("ET\n");

        String period = report.getPeriod() != null ? report.getPeriod() : "custom";
        sb.append("BT\n/F2 9 Tf\n0.22 0.27 0.36 rg\n");
        sb.append("1 0 0 1 ").append(fmt(titleX)).append(' ').append(fmt(y - 42f)).append(" Tm\n");
        sb.append("(Period: ").append(escapePdf(period)).append(") Tj\n");
        sb.append("ET\n");

        y -= 52f;

        // 3. Summary pill badges
        SaleReportSummaryResponse summary = report.getSummary();
        String[] badges = {
                "Total Revenue: " + formatCurrency(summary.getTotalRevenue()),
                "Expenses: " + formatCurrency(summary.getExpenses()),
                "Profit Net: " + formatCurrency(summary.getProfitNet()),
                "Total Orders: " + orZero(summary.getTotalOrders()).toPlainString(),
                "Products Sold: " + orZero(summary.getProductsSold()).toPlainString(),
        };
        float badgeY = y - 20f;
        float badgeH = 20f;
        float badgeW = 150f;
        for (int i = 0; i < badges.length; i++) {
            float bx = MARGIN + i * (badgeW + 5f);
            appendRoundRect(sb, bx, badgeY, badgeW, badgeH, THEME_ORANGE_TINT);
            sb.append("BT\n/F1 7 Tf\n0.12 0.23 0.37 rg\n");
            sb.append("1 0 0 1 ").append(fmt(bx + 6f)).append(' ').append(fmt(badgeY + 7f)).append(" Tm\n");
            sb.append('(').append(escapePdf(truncate(badges[i], 30))).append(") Tj\n");
            sb.append("ET\n");
        }

        y = badgeY - 10f;

        // 4. Table header row — navy background, white bold labels
        float headerY = y - HEADER_ROW_H;
        appendRect(sb, MARGIN, headerY, TABLE_RIGHT - MARGIN, HEADER_ROW_H, "0.12 0.23 0.37");
        for (Object[] col : COLS) {
            String label = (String) col[0];
            float cx = (float) col[1];
            float cw = (float) col[2];
            boolean rightAlign = isRightAligned(label);
            float tx = rightAlign ? cx + cw - 4f - textWidth(label, 8) : cx + 4f;
            sb.append("BT\n/F1 8 Tf\n1 1 1 rg\n");
            sb.append("1 0 0 1 ").append(fmt(tx)).append(' ').append(fmt(headerY + 5f)).append(" Tm\n");
            sb.append('(').append(escapePdf(label)).append(") Tj\n");
            sb.append("ET\n");
        }

        y = headerY;

        // 5. Data rows — alternating backgrounds, one BT/ET per cell for absolute positioning
        List<SaleReportDetailResponse> rows = report.getRows() != null ? report.getRows() : List.of();
        int rowIndex = 0;
        for (SaleReportDetailResponse row : rows) {
            float rowY = y - DATA_ROW_H;
            String rowFill = rowIndex % 2 == 0 ? "0.94 0.96 0.98" : "1 1 1";  // #F0F4FA / white
            appendRect(sb, MARGIN, rowY, TABLE_RIGHT - MARGIN, DATA_ROW_H, rowFill);
            appendHLine(sb, MARGIN, rowY, TABLE_RIGHT - MARGIN, "0.80 0.84 0.89");

            String[][] cells = {
                    { orEmpty(row.getDate()),                                     "left"  },
                    { orEmpty(row.getDay()),                                      "left"  },
                    { orEmpty(row.getTime()),                                     "left"  },
                    { truncate(row.getProduct(), 26),                             "left"  },
                    { truncate(row.getCategory(), 14),                            "left"  },
                    { formatCurrency(row.getPrice()),                             "right" },
                    { row.getQty() != null ? String.valueOf(row.getQty()) : "0",  "right" },
                    { formatCurrency(row.getTotalRevenue()),                      "right" },
            };
            for (int c = 0; c < COLS.length; c++) {
                float cx = (float) COLS[c][1];
                float cw = (float) COLS[c][2];
                boolean ra = "right".equals(cells[c][1]);
                float tx = ra ? cx + cw - 4f - textWidth(cells[c][0], 8) : cx + 4f;
                // Revenue column (index 7) uses the orange accent to echo the UI figures.
                String cellColor = c == 7 ? THEME_ORANGE : THEME_TEXT;
                sb.append("BT\n/F2 8 Tf\n").append(cellColor).append(" rg\n");
                sb.append("1 0 0 1 ").append(fmt(tx)).append(' ').append(fmt(rowY + 4f)).append(" Tm\n");
                sb.append('(').append(escapePdf(cells[c][0])).append(") Tj\n");
                sb.append("ET\n");
            }

            y = rowY;
            rowIndex++;
        }

        // 6. Bottom border of the whole table
        appendHLine(sb, MARGIN, y, TABLE_RIGHT - MARGIN, "0.12 0.23 0.37");

        // 7. Column dividers through header + all rows
        sb.append("0.80 0.84 0.89 RG\n0.4 w\n");
        for (int c = 1; c < COLS.length; c++) {
            float lx = (float) COLS[c][1] - 1f;
            sb.append(fmt(lx)).append(' ').append(fmt(y)).append(" m\n");
            sb.append(fmt(lx)).append(' ').append(fmt(headerY + HEADER_ROW_H)).append(" l S\n");
        }

        // 8. Footer bar
        appendRect(sb, 0, 0, PAGE_W, 18f, "0.12 0.23 0.37");
        sb.append("BT\n/F2 7 Tf\n1 1 1 rg\n");
        sb.append("1 0 0 1 ").append(fmt(MARGIN)).append(" 5 Tm\n");
        sb.append("(Generated by Sales Report System) Tj\n");
        sb.append("ET\n");

        return sb.toString();
    }

    /** Numeric columns are right-aligned; everything else is left-aligned. */
    private boolean isRightAligned(String label) {
        return "Price".equals(label) || "Qty".equals(label) || "Revenue".equals(label);
    }

    /** Append a filled rectangle: x y w h, colour = "r g b" string (0-1). */
    private void appendRect(StringBuilder sb, float x, float y, float w, float h, String rgbFill) {
        sb.append(rgbFill).append(" rg\n");
        sb.append(fmt(x)).append(' ').append(fmt(y)).append(' ')
                .append(fmt(w)).append(' ').append(fmt(h)).append(" re f\n");
    }

    /**
     * Approximate round-rect via a plain rect (PDF 1.4 content streams have no native
     * round-rect operator), kept as a separate method for intent at the call site.
     */
    private void appendRoundRect(StringBuilder sb, float x, float y, float w, float h, String rgbFill) {
        appendRect(sb, x, y, w, h, rgbFill);
    }

    /** Append a 0.4-pt horizontal line from x to x+w at height y. */
    private void appendHLine(StringBuilder sb, float x, float y, float w, String rgbStroke) {
        sb.append(rgbStroke).append(" RG\n0.4 w\n");
        sb.append(fmt(x)).append(' ').append(fmt(y)).append(" m\n");
        sb.append(fmt(x + w)).append(' ').append(fmt(y)).append(" l S\n");
    }

    /** Format a float to 2 dp, stripping trailing zeros for compact PDF operators. */
    private String fmt(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * Rough character-width estimate for Helvetica at a given font size, accurate
     * enough to right-align numeric columns without a font-metrics table.
     */
    private float textWidth(String text, int fontSize) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return text.length() * fontSize * 0.52f;
    }

    /** Truncate to maxLen characters, appending "~" when shortened. */
    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen - 1) + "~";
    }

    /** Return an empty string for null values so PDF cells never render "null". */
    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private String escapePdf(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("#,##0.##");
        return "Rs. " + formatter.format(safeAmount);
    }

    private String buildFileName(SaleReportFilterRequest filter, String format) {
        return "sales-report-" + filter.getStartDate() + "-to-" + filter.getEndDate() + "." + format;
    }

    private LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must be in yyyy-MM-dd format");
        }
    }

    private YearMonth parseMonth(String raw) {
        try {
            return YearMonth.parse(raw.trim(), MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month must be in yyyy-MM format");
        }
    }

    private String resolveDay(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE_FORMAT).format(DAY_FORMAT);
        } catch (DateTimeParseException e) {
            log.debug("Could not parse date '{}' for day-of-week resolution", rawDate);
            return null;
        }
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        Matcher matcher = MONEY_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(matcher.group().replace(",", ""));
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalize(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }
}

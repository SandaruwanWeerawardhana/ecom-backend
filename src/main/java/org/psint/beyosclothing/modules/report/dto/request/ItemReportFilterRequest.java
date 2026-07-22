package org.psint.beyosclothing.modules.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.products.entity.Product;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Common filter for all item report endpoints.
 * Holds the date window plus the optional sales-detail pagination and product
 * filters. Raw string inputs are validated and normalised through
 * {@link #normalised()} before use.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemReportFilterRequest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_LIMIT = 100;

    private String startDate;  // YYYY-MM-DD, optional (default today)
    private String endDate;    // YYYY-MM-DD, optional (default today)
    private String startTime;  // HH:mm, optional (default 00:00)
    private String endTime;    // HH:mm, optional (default 23:59:59.999999999)

    // Sales-detail only fields
    private Integer page;          // 1-based, optional (default 1)
    private Integer limit;         // optional (default 10, max 100)
    private String search;         // optional product-name search
    private String type;           // optional SIMPLE | VARIABLE
    private String categoryId;     // optional numeric category id

}

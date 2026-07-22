package org.psint.beyosclothing.modules.report.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.psint.beyosclothing.modules.report.dto.response.DailyOrderCountResponse;
import org.psint.beyosclothing.modules.report.dto.response.DashboardOverviewResponse;
import org.psint.beyosclothing.modules.report.service.DashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the admin dashboard overview by aggregating directly from the order,
 * POS and customer tables:
 * <ul>
 *   <li><b>Daily revenue</b> – active order subtotals plus completed POS cart
 *       subtotals created today.</li>
 *   <li><b>Monthly revenue</b> – the same, over the current calendar month.</li>
 *   <li><b>Total orders</b> – all active orders plus all completed POS carts.</li>
 *   <li><b>Total customers</b> – active online customers plus active POS customers.</li>
 * </ul>
 * A "completed" POS cart is one that has been checked out (not a draft and no
 * longer active); an "active" order is one in the fulfilment lifecycle from
 * PROCESSING through to DELIVERED/COMPLETED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DAYS_IN_WINDOW = 7;
    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final OrderRepository orderRepository;
    private final PosCartRepository posCartRepository;
    private final CustomerRepository customerRepository;
    private final PosCustomerRepository posCustomerRepository;

    @Override
    public DashboardOverviewResponse getOverview() {
        LocalDate today = LocalDate.now(SRI_LANKA_ZONE);
        YearMonth currentMonth = YearMonth.now(SRI_LANKA_ZONE);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);


        Object[] orderStats = singleRow(
                orderRepository.aggregateDashboardOrderStats(monthStart, monthEnd, dayStart, dayEnd));
        Object[] posStats = singleRow(
                posCartRepository.aggregateDashboardCartStats(monthStart, monthEnd, dayStart, dayEnd));

        long totalOrders = toLong(orderStats[0]) + toLong(posStats[0]);
        BigDecimal monthlyRevenue = toBigDecimal(orderStats[1]).add(toBigDecimal(posStats[1]));
        BigDecimal dailyRevenue = toBigDecimal(orderStats[2]).add(toBigDecimal(posStats[2]));
        long totalCustomers = customerRepository.countByIsActiveTrue()
                + posCustomerRepository.countByIsActiveTrue();

        return DashboardOverviewResponse.builder()
                .dailyRevenue(dailyRevenue)
                .totalOrders(totalOrders)
                .totalCustomers(totalCustomers)
                .monthlyRevenue(monthlyRevenue)
                .build();
    }

    @Override
    public List<DailyOrderCountResponse> getDailyOrdersLast7Days() {
        LocalDate today = LocalDate.now(SRI_LANKA_ZONE);
        LocalDate windowStartDate = today.minusDays(DAYS_IN_WINDOW - 1L);
        LocalDateTime windowStart = windowStartDate.atStartOfDay();
        LocalDateTime windowEnd = today.atTime(LocalTime.MAX);

        // One aggregate query per source, then merge the per-day counts and revenue by date.
        Map<LocalDate, Long> ordersByDay = new HashMap<>();
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        accumulateDailyTotals(ordersByDay, revenueByDay,
                orderRepository.aggregateDailyActiveOrderCounts(windowStart, windowEnd));
        accumulateDailyTotals(ordersByDay, revenueByDay,
                posCartRepository.aggregateDailyCompletedCartCounts(windowStart, windowEnd));

        // Emit every day in the rolling 7-day window so the chart always renders fully, zero-filled.
        List<DailyOrderCountResponse> week = new ArrayList<>(DAYS_IN_WINDOW);
        for (int i = 0; i < DAYS_IN_WINDOW; i++) {
            LocalDate day = windowStartDate.plusDays(i);
            week.add(DailyOrderCountResponse.builder()
                    .date(day.format(DATE_FORMAT))
                    .day(day.format(DAY_LABEL_FORMAT))
                    .orders(ordersByDay.getOrDefault(day, 0L))
                    .revenue(revenueByDay.getOrDefault(day, BigDecimal.ZERO))
                    .build());
        }
        return week;
    }

    /**
     * Merge grouped {@code {year, month, day, count, subtotal}} rows into the running per-day
     * order counts and revenue. Rows come straight from the DB aggregates, so each is an
     * {@code Object[]} of three date parts, a count and a subtotal sum.
     */
    private void accumulateDailyTotals(Map<LocalDate, Long> countTarget,
                                       Map<LocalDate, BigDecimal> revenueTarget,
                                       List<Object[]> rows) {
        for (Object[] row : rows) {
            LocalDate date = LocalDate.of(toInt(row[0]), toInt(row[1]), toInt(row[2]));
            countTarget.merge(date, toLong(row[3]), Long::sum);
            revenueTarget.merge(date, toBigDecimal(row[4]), BigDecimal::add);
        }
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) value).doubleValue());
    }

    /**
     * Unwrap the single {@code {count, monthRevenue, dayRevenue}} row of a dashboard
     * aggregate query, falling back to all-zero stats if the query returned no rows.
     */
    private Object[] singleRow(List<Object[]> rows) {
        return rows.isEmpty()
                ? new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO}
                : rows.getFirst();
    }
}

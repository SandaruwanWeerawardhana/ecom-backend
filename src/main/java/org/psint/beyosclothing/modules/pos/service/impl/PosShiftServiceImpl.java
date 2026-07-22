package org.psint.beyosclothing.modules.pos.service.impl;

import org.psint.beyosclothing.modules.pos.dto.request.CloseShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.request.OpenShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftSummaryResponse;
import org.psint.beyosclothing.modules.pos.service.PosShiftService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PosShiftServiceImpl implements PosShiftService {


    private static final long CLOSED_SHIFT_RETENTION_HOURS = 24;
    private static final long CLEANUP_INTERVAL_MILLIS = 60L * 60L * 1000L;

    private final Map<String, PosShiftResponse> shifts = new ConcurrentHashMap<>();
    private final Map<String, String> activeShiftByCashier = new ConcurrentHashMap<>();

    @Override
    public PosShiftResponse openShift(OpenShiftRequest request) {
        if (request.getCashierId() == null || request.getTerminalId() == null) {
            throw new IllegalArgumentException("cashierId and terminalId are required");
        }
        String cashierKey = String.valueOf(request.getCashierId());
        if (activeShiftByCashier.containsKey(cashierKey)) {
            throw new IllegalStateException("An active shift already exists for this cashier");
        }
        String uuid = UUID.randomUUID().toString();
        PosShiftResponse shift = PosShiftResponse.builder()
                .uuid(uuid)
                .cashierId(request.getCashierId())
                .terminalId(request.getTerminalId())
                .openedAt(LocalDateTime.now())
                .openingBalance(Optional.ofNullable(request.getOpeningBalance()).orElse(BigDecimal.ZERO))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        shifts.put(uuid, shift);
        activeShiftByCashier.put(cashierKey, uuid);
        return shift;
    }

    @Override
    public PosShiftResponse closeShift(String shiftUuid, CloseShiftRequest request) {
        PosShiftResponse shift = shifts.get(shiftUuid);
        if (shift == null) {
            throw new IllegalArgumentException("Shift not found");
        }
        if (shift.getClosedAt() != null) {
            throw new IllegalStateException("Shift already closed");
        }
        BigDecimal expected = Optional.ofNullable(shift.getOpeningBalance()).orElse(BigDecimal.ZERO);
        BigDecimal closing = Optional.ofNullable(request.getClosingBalance()).orElse(BigDecimal.ZERO);
        shift.setClosingBalance(closing);
        shift.setExpectedBalance(expected);
        shift.setDifference(closing.subtract(expected));
        shift.setClosedAt(LocalDateTime.now());
        shift.setNotes(request.getNotes());
        shift.setUpdatedAt(LocalDateTime.now());
        activeShiftByCashier.remove(String.valueOf(shift.getCashierId()));
        return shift;
    }

    /**
     * Evicts shifts that were closed more than the retention window ago.
     * Without this, the in-memory shift map grows for the lifetime of the JVM.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MILLIS)
    public void evictExpiredClosedShifts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(CLOSED_SHIFT_RETENTION_HOURS);
        shifts.values().removeIf(shift ->
                shift.getClosedAt() != null && shift.getClosedAt().isBefore(cutoff));
    }

    @Override
    public PosShiftResponse getCurrentShiftForCashier(String cashierUuid) {
        String uuid = activeShiftByCashier.get(cashierUuid);
        if (uuid == null) return null;
        return shifts.get(uuid);
    }

    @Override
    public PosShiftResponse getShiftByUuid(String shiftUuid) {
        return shifts.get(shiftUuid);
    }

    @Override
    public PosShiftSummaryResponse getShiftSummary(String shiftUuid) {
        PosShiftResponse shift = shifts.get(shiftUuid);
        if (shift == null) throw new IllegalArgumentException("Shift not found");
        return PosShiftSummaryResponse.builder()
                .uuid(shiftUuid)
                .totalSales(Optional.ofNullable(shift.getExpectedBalance()).orElse(BigDecimal.ZERO))
                .cashSales(BigDecimal.ZERO)
                .cardSales(BigDecimal.ZERO)
                .transactionCount(0)
                .build();
    }
}

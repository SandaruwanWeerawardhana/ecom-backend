package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.request.CloseShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.request.OpenShiftRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosShiftSummaryResponse;

public interface PosShiftService {
    PosShiftResponse openShift(OpenShiftRequest request);
    PosShiftResponse closeShift(String shiftUuid, CloseShiftRequest request);
    PosShiftResponse getCurrentShiftForCashier(String cashierUuid);
    PosShiftResponse getShiftByUuid(String shiftUuid);
    PosShiftSummaryResponse getShiftSummary(String shiftUuid);
}

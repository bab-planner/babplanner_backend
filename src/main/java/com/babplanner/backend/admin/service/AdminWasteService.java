package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.MenuWasteResponse;
import com.babplanner.backend.admin.dto.WasteSummaryResponse;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 잔반 집계.
 *
 * <p>기간은 메뉴의 제공일로 센다. 제출 시각으로 세면 늦게 올린 사진이 다음
 * 날짜 급식의 잔반으로 잡힌다.
 *
 * <p>건수는 검토가 끝난(APPROVED, REJECTED) 제출이고, 평균은 그중 APPROVED이면서
 * 잔반률이 기록된 건뿐이다. 수동 검토에서는 잔반률이 기록되지 않으므로 평균은
 * null, 메뉴별 집계는 빈 배열일 수 있다.
 */
@Service
public class AdminWasteService {

    /** 잔반률 평균을 낼 때 보는 상태. */
    private static final WasteSubmissionStatus MEASURED = WasteSubmissionStatus.APPROVED;

    /** 검토가 끝난 제출. 건수는 이 둘을 센다. */
    private static final List<WasteSubmissionStatus> REVIEWED =
            List.of(WasteSubmissionStatus.APPROVED, WasteSubmissionStatus.REJECTED);

    private final WasteSubmissionRepository wasteSubmissionRepository;

    public AdminWasteService(WasteSubmissionRepository wasteSubmissionRepository) {
        this.wasteSubmissionRepository = wasteSubmissionRepository;
    }

    @Transactional(readOnly = true)
    public WasteSummaryResponse getSummary(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return WasteSummaryResponse.from(
                wasteSubmissionRepository.aggregateWasteRate(MEASURED, REVIEWED, from, to));
    }

    @Transactional(readOnly = true)
    public List<MenuWasteResponse> getByMenu(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return wasteSubmissionRepository.aggregateWasteRateByMenu(MEASURED, from, to).stream()
                .map(MenuWasteResponse::from)
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
    }
}

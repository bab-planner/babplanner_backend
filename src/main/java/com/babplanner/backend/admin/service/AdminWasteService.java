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
 * <p>표본은 APPROVED이면서 잔반률이 나온 건뿐이다. 분석에 실패했거나 거절된
 * 건은 잔반률을 잰 적이 없으므로 평균에 넣을 값이 없다.
 */
@Service
public class AdminWasteService {

    /** 분석이 끝나 잔반률이 확정된 상태. */
    private static final WasteSubmissionStatus ANALYZED = WasteSubmissionStatus.APPROVED;

    private final WasteSubmissionRepository wasteSubmissionRepository;

    public AdminWasteService(WasteSubmissionRepository wasteSubmissionRepository) {
        this.wasteSubmissionRepository = wasteSubmissionRepository;
    }

    @Transactional(readOnly = true)
    public WasteSummaryResponse getSummary(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return WasteSummaryResponse.from(
                wasteSubmissionRepository.aggregateWasteRate(ANALYZED, from, to));
    }

    @Transactional(readOnly = true)
    public List<MenuWasteResponse> getByMenu(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return wasteSubmissionRepository.aggregateWasteRateByMenu(ANALYZED, from, to).stream()
                .map(MenuWasteResponse::from)
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
    }
}

package com.babplanner.backend.admin.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.congestion.dto.CongestionResponse;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 혼잡도 시계열.
 *
 * <p>저장된 관측을 시간 순으로 늘어놓기만 한다. 구간 평균이나 시간대 묶음을
 * 만들지 않는 것은, 어떤 폭으로 묶을지가 아직 정해진 적이 없기 때문이다.
 * 임의로 정한 묶음은 화면에 그럴듯하게 나오지만 원래 값과 다르다.
 *
 * <p>관측이 없으면 빈 배열이다. 이 단계에는 관측을 넣는 경로가 없으므로 처음
 * 저장돼 있던 현재값 말고는 비어 있는 것이 정상이다.
 */
@Service
public class AdminCongestionService {

    private final CongestionObservationRepository observationRepository;
    private final DiningSiteRepository diningSiteRepository;

    public AdminCongestionService(
            CongestionObservationRepository observationRepository,
            DiningSiteRepository diningSiteRepository) {
        this.observationRepository = observationRepository;
        this.diningSiteRepository = diningSiteRepository;
    }

    @Transactional(readOnly = true)
    public List<CongestionResponse> getTimeseries(Long siteId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            // 뒤집힌 기간은 빈 결과가 아니라 잘못된 요청이다. 빈 배열로
            // 돌려주면 "그 기간에 관측이 없다"로 읽힌다.
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }

        return observationRepository
                .findBySiteIdAndObservedAtBetweenOrderByObservedAtAscIdAsc(
                        resolveSiteId(siteId), from, to)
                .stream()
                .map(CongestionResponse::from)
                .toList();
    }

    /** 없는 식당을 물으면 빈 배열 대신 거절한다. 오타를 "관측 없음"으로 숨기지 않는다. */
    private Long resolveSiteId(Long siteId) {
        if (siteId == null) {
            return diningSiteRepository
                    .findFirstByOrderBySortOrderAscIdAsc()
                    .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_DINING_SITE))
                    .getId();
        }
        if (!diningSiteRepository.existsById(siteId)) {
            throw new ApiException(ErrorCode.UNKNOWN_DINING_SITE);
        }
        return siteId;
    }
}

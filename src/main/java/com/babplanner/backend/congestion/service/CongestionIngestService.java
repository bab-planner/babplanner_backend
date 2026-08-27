package com.babplanner.backend.congestion.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.domain.CongestionStatus;
import com.babplanner.backend.congestion.dto.CongestionObservationRequest;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 혼잡도 관측 수집.
 *
 * <p>이력과 현재값을 <b>한 트랜잭션에서 같이</b> 쓴다. Stage 23까지는 이력
 * 테이블에 쓰는 주체가 없어 현재값과 이력이 갈라질 수 있었다. 여기가 그
 * 유일한 정상 경로다. 현재값만 따로 고치는 길을 새로 만들지 않는다.
 *
 * <p>한쪽만 저장되면 안 된다. 이력에는 있는데 현재값이 옛날 것이거나, 현재값은
 * 새것인데 이력에 없으면 나중에 무엇이 맞는지 판단할 근거가 사라진다.
 */
@Service
public class CongestionIngestService {

    /**
     * 저장되는 출처.
     *
     * <p>보내는 쪽이 정하지 않는다. 이 경로로 들어온 값은 전부 수집
     * 파이프라인이 판정한 결과이므로 서버가 그렇게 기록한다.
     */
    public static final String SOURCE = "CCTV_AI";

    private final CongestionObservationRepository observationRepository;
    private final CongestionStatusRepository statusRepository;
    private final DiningSiteRepository siteRepository;
    private final Clock clock;

    public CongestionIngestService(
            CongestionObservationRepository observationRepository,
            CongestionStatusRepository statusRepository,
            DiningSiteRepository siteRepository,
            Clock clock) {
        this.observationRepository = observationRepository;
        this.statusRepository = statusRepository;
        this.siteRepository = siteRepository;
        this.clock = clock;
    }

    @Transactional
    public void ingest(CongestionObservationRequest request) {
        if (request.hasBlankModelVersion()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        DiningSite site = siteRepository
                .findById(request.siteId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_DINING_SITE));

        Instant observedAt = request.observedAt().toInstant();
        Instant now = clock.instant();
        String displayMessage = request.normalizedDisplayMessage();

        // 이력에는 늦게 온 관측도 남긴다. 순서가 뒤집혀 도착했다는 사실
        // 자체가 나중에 장비를 의심할 근거가 된다.
        observationRepository.save(new CongestionObservation(
                site,
                request.level(),
                request.peopleCount(),
                displayMessage,
                SOURCE,
                request.deviceId(),
                request.normalizedModelVersion(),
                observedAt,
                now));

        statusRepository
                .findBySiteId(site.getId())
                .ifPresentOrElse(
                        current -> updateIfNotOlder(current, request, displayMessage, observedAt, now),
                        () -> statusRepository.save(new CongestionStatus(
                                site,
                                request.level(),
                                request.peopleCount(),
                                displayMessage,
                                SOURCE,
                                observedAt,
                                now)));
    }

    /**
     * 늦게 도착한 과거 관측이 현재값을 되돌리지 않게 한다.
     *
     * <p>네트워크가 잠깐 끊겼다 복구되면 밀린 관측이 뒤늦게 한꺼번에 온다.
     * 그때 마지막에 도착한 것으로 덮으면 화면의 "지금"이 30분 전으로 돌아간다.
     *
     * <p>관측 시각이 같으면 나중에 처리된 쪽으로 갱신한다. 같은 순간에 대한
     * 두 판정 중 어느 것이 옳은지 판단할 근거가 없고, 재처리로 보낸 수정값이
     * 조용히 버려지지 않는 쪽이 낫다.
     */
    private void updateIfNotOlder(
            CongestionStatus current,
            CongestionObservationRequest request,
            String displayMessage,
            Instant observedAt,
            Instant now) {
        if (observedAt.isBefore(current.getObservedAt())) {
            return;
        }
        current.apply(
                request.level(),
                request.peopleCount(),
                displayMessage,
                SOURCE,
                observedAt,
                now);
    }
}

package com.babplanner.backend.waste.service;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 처리 도중의 상태 변경만 담당한다.
 *
 * <p>각 단계를 짧은 독립 트랜잭션으로 끊는다. 분석이 오래 걸려도 커넥션을
 * 붙잡고 있지 않고, 중간에 죽어도 직전까지의 상태가 남아 재시작 복구가
 * 이어받을 수 있다.
 */
@Component
public class WasteSubmissionStateStore {

    private final WasteSubmissionRepository submissionRepository;
    private final Clock clock;

    public WasteSubmissionStateStore(WasteSubmissionRepository submissionRepository, Clock clock) {
        this.submissionRepository = submissionRepository;
        this.clock = clock;
    }

    /**
     * 기대 상태일 때만 다음 상태로 원자적으로 옮긴다.
     *
     * @return 이번 호출이 선점에 성공했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long id, WasteSubmissionStatus expected, WasteSubmissionStatus next) {
        return submissionRepository.claimForProcessing(id, expected, next, clock.instant()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<WasteSubmission> find(Long id) {
        return submissionRepository.findById(id);
    }

    /** 분석 결과와 최종 상태를 함께 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            Long id,
            WasteSubmissionStatus finalStatus,
            Double wasteRate,
            Double confidence,
            String modelVersion) {
        submissionRepository
                .findById(id)
                .ifPresent(submission -> {
                    // 이미 종료된 건은 덮어쓰지 않는다.
                    if (submission.getStatus().isTerminal()) {
                        return;
                    }
                    submission.applyAnalysis(
                            wasteRate, confidence, modelVersion, clock.instant());
                    submission.transitionTo(finalStatus, clock.instant());
                });
    }

    /**
     * 실패로 종료한다.
     *
     * <p>실패 사유는 사용자 데이터가 섞일 수 있어 저장하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id) {
        submissionRepository
                .findById(id)
                .ifPresent(submission -> {
                    if (submission.getStatus().isTerminal()) {
                        return;
                    }
                    submission.transitionTo(WasteSubmissionStatus.FAILED, clock.instant());
                });
    }
}

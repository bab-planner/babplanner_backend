package com.babplanner.backend.waste.service;

import com.babplanner.backend.waste.analysis.WasteAnalysisDecision;
import com.babplanner.backend.waste.analysis.WasteAnalysisInput;
import com.babplanner.backend.waste.analysis.WasteAnalysisResult;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 제출 한 건을 RECEIVED에서 종료 상태까지 옮긴다.
 *
 * <p>여기까지 오는 경로는 WasteProcessingDispatcher 하나뿐이며, 실행되는
 * 스레드는 항상 분석 전용 executor의 작업 스레드다.
 *
 * <p>각 단계는 원자적 선점(claim)으로 시작한다. 재시작 복구와 정상 흐름이
 * 같은 건을 동시에 집어도 한 쪽만 진행한다.
 */
@Component
public class WasteSubmissionProcessor {

    private static final Logger log = LoggerFactory.getLogger(WasteSubmissionProcessor.class);

    private final WasteSubmissionStateStore stateStore;
    private final WasteImageStorage imageStorage;
    private final WasteAnalyzer analyzer;

    public WasteSubmissionProcessor(
            WasteSubmissionStateStore stateStore,
            WasteImageStorage imageStorage,
            WasteAnalyzer analyzer) {
        this.stateStore = stateStore;
        this.imageStorage = imageStorage;
        this.analyzer = analyzer;
    }

    /**
     * 한 건을 처리한다.
     *
     * <p>어느 단계에서 실패하든 FAILED로 끝낸다. 진행 중 상태로 남겨두면
     * 사용자 화면이 영영 돌아간다.
     */
    public void process(Long submissionId) {
        try {
            if (!stateStore.claim(
                    submissionId, WasteSubmissionStatus.RECEIVED, WasteSubmissionStatus.VERIFYING)) {
                // 다른 쪽이 이미 가져갔다.
                return;
            }
            Optional<WasteSubmission> loaded = stateStore.find(submissionId);
            if (loaded.isEmpty()) {
                return;
            }
            WasteSubmission submission = loaded.get();

            if (!imageStorage.exists(submission.getImageStorageKey())) {
                // 저장된 이미지가 없으면 분석할 수 없다.
                stateStore.fail(submissionId);
                return;
            }

            if (!stateStore.claim(
                    submissionId,
                    WasteSubmissionStatus.VERIFYING,
                    WasteSubmissionStatus.ANALYZING)) {
                return;
            }

            WasteAnalysisResult result = analyzer.analyze(new WasteAnalysisInput(
                    submission.getId(), submission.getMenuId(), submission.getImageStorageKey()));

            WasteSubmissionStatus finalStatus =
                    result.decision() == WasteAnalysisDecision.APPROVED
                            ? WasteSubmissionStatus.APPROVED
                            : WasteSubmissionStatus.REJECTED;
            // 리워드는 여기서 지급하지 않는다. 지급 규칙이 아직 없으므로
            // earnedPoints/couponIssued는 null로 남는다.
            stateStore.complete(
                    submissionId,
                    finalStatus,
                    result.wasteRate(),
                    result.confidence(),
                    result.modelVersion());
        } catch (RuntimeException exception) {
            // 사용자 데이터가 섞일 수 있어 예외 메시지는 남기지 않는다.
            log.warn("잔반 제출 처리에 실패했습니다. id={}", submissionId);
            safelyFail(submissionId);
        }
    }

    private void safelyFail(Long submissionId) {
        try {
            stateStore.fail(submissionId);
        } catch (RuntimeException exception) {
            log.error("잔반 제출을 실패 상태로 기록하지 못했습니다. id={}", submissionId);
        }
    }
}

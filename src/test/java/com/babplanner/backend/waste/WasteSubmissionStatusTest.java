package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상태 전이 규칙. 되돌아가거나 건너뛰는 전이를 막는다. */
class WasteSubmissionStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-26T03:00:00Z");

    private WasteSubmission submission() {
        return new WasteSubmission(1L, 2L, "storage-key", NOW, NOW);
    }

    @Test
    @DisplayName("정상 경로는 RECEIVED → VERIFYING → ANALYZING → APPROVED 다")
    void happyPath() {
        assertThat(WasteSubmissionStatus.RECEIVED.canTransitionTo(WasteSubmissionStatus.VERIFYING))
                .isTrue();
        assertThat(WasteSubmissionStatus.VERIFYING.canTransitionTo(WasteSubmissionStatus.ANALYZING))
                .isTrue();
        assertThat(WasteSubmissionStatus.ANALYZING.canTransitionTo(WasteSubmissionStatus.APPROVED))
                .isTrue();
        assertThat(WasteSubmissionStatus.ANALYZING.canTransitionTo(WasteSubmissionStatus.REJECTED))
                .isTrue();
    }

    @Test
    @DisplayName("어느 진행 단계에서든 FAILED로 끝낼 수 있다")
    void anyStepCanFail() {
        for (WasteSubmissionStatus status : WasteSubmissionStatus.values()) {
            if (status.isTerminal()) {
                continue;
            }
            assertThat(status.canTransitionTo(WasteSubmissionStatus.FAILED))
                    .as("%s", status)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("종료 상태는 다시 바뀌지 않는다")
    void terminalIsFinal() {
        for (WasteSubmissionStatus terminal : new WasteSubmissionStatus[] {
            WasteSubmissionStatus.APPROVED,
            WasteSubmissionStatus.REJECTED,
            WasteSubmissionStatus.FAILED
        }) {
            assertThat(terminal.isTerminal()).isTrue();
            for (WasteSubmissionStatus next : WasteSubmissionStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s", terminal, next)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("단계를 건너뛰지 못한다")
    void cannotSkipSteps() {
        assertThat(WasteSubmissionStatus.RECEIVED.canTransitionTo(WasteSubmissionStatus.ANALYZING))
                .isFalse();
        assertThat(WasteSubmissionStatus.RECEIVED.canTransitionTo(WasteSubmissionStatus.APPROVED))
                .isFalse();
        assertThat(WasteSubmissionStatus.VERIFYING.canTransitionTo(WasteSubmissionStatus.APPROVED))
                .isFalse();
    }

    @Test
    @DisplayName("진행 중이던 건만 RECEIVED로 되돌릴 수 있다")
    void onlyInProgressCanBeRewound() {
        // 재시작 복구가 쓰는 경로다.
        assertThat(WasteSubmissionStatus.VERIFYING.canTransitionTo(WasteSubmissionStatus.RECEIVED))
                .isTrue();
        assertThat(WasteSubmissionStatus.ANALYZING.canTransitionTo(WasteSubmissionStatus.RECEIVED))
                .isTrue();
        assertThat(WasteSubmissionStatus.RECEIVED.canTransitionTo(WasteSubmissionStatus.RECEIVED))
                .isFalse();
    }

    @Test
    @DisplayName("새 제출은 RECEIVED로 시작한다")
    void startsAsReceived() {
        assertThat(submission().getStatus()).isEqualTo(WasteSubmissionStatus.RECEIVED);
    }

    @Test
    @DisplayName("허용되지 않는 전이는 예외로 거부한다")
    void invalidTransitionThrows() {
        WasteSubmission submission = submission();

        assertThatThrownBy(
                        () -> submission.transitionTo(WasteSubmissionStatus.APPROVED, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(submission.getStatus()).isEqualTo(WasteSubmissionStatus.RECEIVED);
    }

    @Test
    @DisplayName("분석 결과를 기록해도 리워드 필드는 비어 있다")
    void analysisDoesNotTouchReward() {
        WasteSubmission submission = submission();

        submission.applyAnalysis(0.15, 0.92, "fixture-waste-v1", NOW);

        assertThat(submission.getWasteRate()).isEqualTo(0.15);
        assertThat(submission.getConfidence()).isEqualTo(0.92);
        assertThat(submission.getAnalysisModelVersion()).isEqualTo("fixture-waste-v1");
        assertThat(submission.getEarnedPoints()).isNull();
        assertThat(submission.getCouponIssued()).isNull();
    }

    @Test
    @DisplayName("상태 문자열이 OpenAPI enum과 같다")
    void statusStringsMatchContract() {
        assertThat(java.util.Arrays.stream(WasteSubmissionStatus.values()).map(Enum::name).toList())
                .containsExactly(
                        "RECEIVED", "VERIFYING", "ANALYZING", "APPROVED", "REJECTED", "FAILED");
    }
}

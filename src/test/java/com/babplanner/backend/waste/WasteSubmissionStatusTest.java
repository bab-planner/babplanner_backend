package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상태 값과 초기 상태. 상태 전이는 관리자 검토 API가 조건부 UPDATE로 처리한다. */
class WasteSubmissionStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-26T03:00:00Z");

    @Test
    @DisplayName("새 제출은 PENDING으로 시작하고 리워드 필드가 비어 있다")
    void startsAsPending() {
        WasteSubmission submission = new WasteSubmission(1L, 2L, "storage-key", NOW, NOW);

        assertThat(submission.getStatus()).isEqualTo(WasteSubmissionStatus.PENDING);
        assertThat(submission.getEarnedPoints()).isNull();
        assertThat(submission.getCouponIssued()).isNull();
        assertThat(submission.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("예전 상태 값을 지우지 않고 PENDING을 더했다")
    void keepsLegacyValues() {
        assertThat(java.util.Arrays.stream(WasteSubmissionStatus.values()).map(Enum::name).toList())
                .contains(
                        "PENDING", "APPROVED", "REJECTED", "RECEIVED", "VERIFYING", "ANALYZING",
                        "FAILED");
    }
}

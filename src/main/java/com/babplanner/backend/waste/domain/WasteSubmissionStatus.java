package com.babplanner.backend.waste.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * OpenAPI v3.1 WasteSubmission.status 열거값.
 *
 * <p>허용 전이를 여기 한 곳에 모아 둔다. Controller나 서비스 여기저기에
 * 흩어지면 잘못된 전이를 막을 수 없다.
 */
public enum WasteSubmissionStatus {
    RECEIVED,
    VERIFYING,
    ANALYZING,
    APPROVED,
    REJECTED,
    FAILED;

    private static final Set<WasteSubmissionStatus> TERMINAL =
            EnumSet.of(APPROVED, REJECTED, FAILED);

    /** 더 이상 바뀌지 않는 상태. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** 처리가 진행 중인 상태. 재기동 복구 대상이기도 하다. */
    public boolean isInProgress() {
        return this == VERIFYING || this == ANALYZING;
    }

    /** 이 상태에서 next로 넘어갈 수 있는지. */
    public boolean canTransitionTo(WasteSubmissionStatus next) {
        if (isTerminal()) {
            return false;
        }
        return switch (next) {
            // 실패는 진행 중 어느 지점에서나 가능하다.
            case FAILED -> true;
            case VERIFYING -> this == RECEIVED;
            case ANALYZING -> this == VERIFYING;
            case APPROVED, REJECTED -> this == ANALYZING;
            // RECEIVED로 되돌리는 것은 재기동 복구에서만 쓰며 진행 중일 때만 허용한다.
            case RECEIVED -> isInProgress();
        };
    }
}

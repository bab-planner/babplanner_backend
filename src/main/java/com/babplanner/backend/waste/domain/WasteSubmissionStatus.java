package com.babplanner.backend.waste.domain;

/**
 * 잔반 제출 상태.
 *
 * <p>현재 흐름은 PENDING → APPROVED / REJECTED 이며 관리자가 검토한다. APPROVED는
 * 종료 상태다(되돌리지 않고 지급한 포인트를 회수하지 않는다). REJECTED는 이후
 * 승인될 수 있다.
 *
 * <p>RECEIVED, VERIFYING, ANALYZING, FAILED는 예전 처리 방식이 남긴 행을 읽기 위한
 * 값이다. 새로 만들지 않으며 데이터를 지우지 않는다.
 */
public enum WasteSubmissionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RECEIVED,
    VERIFYING,
    ANALYZING,
    FAILED
}

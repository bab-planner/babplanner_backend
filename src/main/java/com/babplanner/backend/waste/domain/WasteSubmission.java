package com.babplanner.backend.waste.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 잔반 사진 제출 한 건.
 *
 * <p>이미지는 저장소 키만 갖는다. 서버 파일 경로는 저장하지도, 내보내지도
 * 않는다.
 *
 * <p>리워드 필드({@code earnedPoints}, {@code couponIssued})는 OpenAPI 계약이
 * 정의하므로 컬럼을 두지만, 이번 단계에서 지급 규칙이 없으므로 항상 null로
 * 남는다. 임의의 값을 채워 정책처럼 보이게 하지 않는다.
 */
@Entity
@Table(name = "waste_submissions")
public class WasteSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WasteSubmissionStatus status;

    @Column(name = "image_storage_key", nullable = false, length = 200)
    private String imageStorageKey;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "waste_rate")
    private Double wasteRate;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "earned_points")
    private Integer earnedPoints;

    @Column(name = "coupon_issued")
    private Boolean couponIssued;

    /** 이 판정을 낸 분석 구현. 내부 추적용이며 API 응답에 넣지 않는다. */
    @Column(name = "analysis_model_version", length = 100)
    private String analysisModelVersion;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WasteSubmission() {
        // JPA 전용
    }

    public WasteSubmission(
            Long userId, Long menuId, String imageStorageKey, Instant capturedAt, Instant now) {
        this.userId = userId;
        this.menuId = menuId;
        this.imageStorageKey = imageStorageKey;
        this.capturedAt = capturedAt;
        this.status = WasteSubmissionStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 상태를 옮긴다.
     *
     * <p>허용되지 않는 전이는 조용히 무시하지 않고 거부한다. 종료 상태는 다시
     * 바뀌지 않는다.
     */
    public void transitionTo(WasteSubmissionStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "허용되지 않는 상태 전이입니다: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    public void markProcessingStarted(Instant now) {
        this.processingStartedAt = now;
        this.updatedAt = now;
    }

    /** 분석 결과를 기록한다. 리워드 필드는 건드리지 않는다. */
    public void applyAnalysis(
            Double wasteRate, Double confidence, String modelVersion, Instant now) {
        this.wasteRate = wasteRate;
        this.confidence = confidence;
        this.analysisModelVersion = modelVersion;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public WasteSubmissionStatus getStatus() {
        return status;
    }

    public String getImageStorageKey() {
        return imageStorageKey;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Double getWasteRate() {
        return wasteRate;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Integer getEarnedPoints() {
        return earnedPoints;
    }

    public Boolean getCouponIssued() {
        return couponIssued;
    }

    public String getAnalysisModelVersion() {
        return analysisModelVersion;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

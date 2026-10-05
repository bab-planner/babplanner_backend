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
 * <p>제출은 PENDING으로 생성되고 관리자가 검토해 APPROVED 또는 REJECTED로
 * 바꾼다. 상태 전이는 동시성 때문에 조건부 UPDATE(repository)로만 일어난다.
 * {@code earnedPoints}는 APPROVED에서 실제 지급된 포인트, REJECTED에서 0이며
 * PENDING에서는 null이다. 쿠폰 발급 규칙은 미정이라 {@code couponIssued}는
 * 종료 상태에서 항상 false다.
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

    /** 측정된 잔반률(0~1). 수동 검토에서는 채워지지 않으며 null일 수 있다. */
    @Column(name = "waste_rate")
    private Double wasteRate;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "earned_points")
    private Integer earnedPoints;

    @Column(name = "coupon_issued")
    private Boolean couponIssued;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

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
        this.status = WasteSubmissionStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 측정된 잔반률을 기록한다. 집계가 읽는 값이다. */
    public void applyMeasurement(Double wasteRate, Double confidence, Instant now) {
        this.wasteRate = wasteRate;
        this.confidence = confidence;
        this.updatedAt = now;
    }

    /**
     * 검토 결과를 엔티티에 직접 기록한다.
     *
     * <p>운영 경로는 동시성 때문에 조건부 UPDATE를 쓴다. 이 메서드는 이미 검토된
     * 상태의 행을 만들어야 하는 곳(데이터 이관, 테스트 fixture)을 위한 것이다.
     */
    public void markReviewed(
            WasteSubmissionStatus status, Integer earnedPoints, Long reviewerId, Instant now) {
        this.status = status;
        this.earnedPoints = earnedPoints;
        this.couponIssued = earnedPoints == null ? null : Boolean.FALSE;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
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

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

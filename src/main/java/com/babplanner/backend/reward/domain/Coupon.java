package com.babplanner.backend.reward.domain;

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
 * 사용자가 보유한 쿠폰.
 *
 * <p>만료는 저장된 status를 요청마다 갱신하지 않고 조회 시점에 판정한다.
 * DB에는 발급 당시 상태만 남고, 만료 여부는 expiresAt과 현재 시각으로
 * 계산한다. 사용(USED)만 실제로 상태를 바꾼다.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    /** 카드에 표시할 배지 문자열. 서버가 주는 표시값일 뿐 할인율 계산은 하지 않는다. */
    @Column(name = "badge_label", length = 20)
    private String badgeLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Coupon() {
        // JPA 전용
    }

    public Coupon(
            Long userId,
            String title,
            String badgeLabel,
            CouponStatus status,
            Instant expiresAt,
            Instant now) {
        this.userId = userId;
        this.title = title;
        this.badgeLabel = badgeLabel;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** 저장된 상태와 만료 시각을 합친 실제 표시 상태. */
    public CouponStatus effectiveStatus(Instant now) {
        if (status == CouponStatus.AVAILABLE && isExpired(now)) {
            return CouponStatus.EXPIRED;
        }
        return status;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getBadgeLabel() {
        return badgeLabel;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

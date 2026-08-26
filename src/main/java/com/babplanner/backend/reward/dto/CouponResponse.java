package com.babplanner.backend.reward.dto;

import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import java.time.Instant;

/** OpenAPI Coupon. */
public record CouponResponse(
        Long id, String title, CouponStatus status, String badgeLabel, String expiresAt) {

    /** 만료는 저장 상태가 아니라 조회 시각 기준으로 판정한다. */
    public static CouponResponse from(Coupon coupon, Instant now) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getTitle(),
                coupon.effectiveStatus(now),
                coupon.getBadgeLabel(),
                coupon.getExpiresAt() == null ? null : coupon.getExpiresAt().toString());
    }
}

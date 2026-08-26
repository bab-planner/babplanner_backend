package com.babplanner.backend.reward.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.reward.dto.CouponResponse;
import com.babplanner.backend.reward.dto.RewardSummaryResponse;
import com.babplanner.backend.reward.dto.RewardTransactionResponse;
import com.babplanner.backend.reward.service.RewardService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리워드 조회와 쿠폰 사용. 모두 bearerAuth가 필요하다.
 *
 * <p>사용자는 인증 principal에서만 얻는다. userId를 query나 body로 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping("/summary")
    public ApiResponse<RewardSummaryResponse> getSummary(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(rewardService.getSummary(principal.id()));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<RewardTransactionResponse>> getTransactions(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(rewardService.getTransactions(principal.id()));
    }

    @GetMapping("/coupons")
    public ApiResponse<List<CouponResponse>> getCoupons(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(rewardService.getCoupons(principal.id()));
    }

    @PostMapping("/coupons/{couponId}/redeem")
    public ApiResponse<CouponResponse> redeemCoupon(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long couponId) {
        return ApiResponse.success(rewardService.redeemCoupon(principal.id(), couponId));
    }
}

package com.babplanner.backend.reward.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.dto.CouponResponse;
import com.babplanner.backend.reward.dto.RewardSummaryResponse;
import com.babplanner.backend.reward.dto.RewardTransactionResponse;
import com.babplanner.backend.reward.repository.CouponRepository;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트/쿠폰 조회와 쿠폰 사용.
 *
 * <p>이번 단계는 이미 만들어진 reward 데이터를 읽고 쿠폰을 사용하는 것까지다.
 * 지급 규칙(설문 참여 시 몇 점, 잔반률 몇 퍼센트면 쿠폰 등)은 아직 확정된
 * 계약이 없으므로 여기에 만들지 않는다.
 */
@Service
public class RewardService {

    private final RewardAccountRepository accountRepository;
    private final RewardTransactionRepository transactionRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    public RewardService(
            RewardAccountRepository accountRepository,
            RewardTransactionRepository transactionRepository,
            CouponRepository couponRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    /** 계정이 아직 없으면 0포인트로 본다. 조회만으로 계정을 만들지 않는다. */
    @Transactional(readOnly = true)
    public RewardSummaryResponse getSummary(Long userId) {
        Instant now = clock.instant();
        int points =
                accountRepository.findByUserId(userId).map(account -> account.getPoints()).orElse(0);
        // 사용 가능 개수는 실제 사용할 수 있는 쿠폰만 센다. 만료된 것은 뺀다.
        long available = couponRepository.findByUserId(userId).stream()
                .filter(coupon -> coupon.effectiveStatus(now) == CouponStatus.AVAILABLE)
                .count();
        return new RewardSummaryResponse(points, (int) available);
    }

    @Transactional(readOnly = true)
    public List<RewardTransactionResponse> getTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(RewardTransactionResponse::from)
                .toList();
    }

    /** 사용 가능한 쿠폰 먼저, 그다음 최신 발급순. */
    @Transactional(readOnly = true)
    public List<CouponResponse> getCoupons(Long userId) {
        Instant now = clock.instant();
        return couponRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(
                                (Coupon coupon) ->
                                        coupon.effectiveStatus(now) == CouponStatus.AVAILABLE
                                                ? 0
                                                : 1)
                        .thenComparing(Coupon::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Coupon::getId, Comparator.reverseOrder()))
                .map(coupon -> CouponResponse.from(coupon, now))
                .toList();
    }

    /**
     * 쿠폰 사용.
     *
     * <p>사용 가능한 쿠폰만 원자적으로 USED로 바꾼다. 이미 사용했거나 만료된
     * 쿠폰은 409다. 남의 쿠폰은 존재 여부를 알리지 않도록 404로 처리한다.
     *
     * <p>포인트를 차감하지 않는다. 포인트로 쿠폰을 사는 계약이 아직 없다.
     */
    @Transactional
    public CouponResponse redeemCoupon(Long userId, Long couponId) {
        Instant now = clock.instant();
        couponRepository
                .findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        int updated = couponRepository.redeemIfAvailable(
                couponId, userId, now, CouponStatus.AVAILABLE, CouponStatus.USED);
        if (updated != 1) {
            // 이미 사용했는지 만료됐는지 응답으로 단정하지 않는다.
            throw new ApiException(ErrorCode.COUPON_NOT_USABLE);
        }

        // 갱신 후 서버 상태를 그대로 돌려준다. 앱이 status를 추측하지 않게 한다.
        return couponRepository
                .findByIdAndUserId(couponId, userId)
                .map(refreshed -> CouponResponse.from(refreshed, now))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}

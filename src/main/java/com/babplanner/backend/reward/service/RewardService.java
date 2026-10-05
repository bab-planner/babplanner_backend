package com.babplanner.backend.reward.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.reward.domain.RewardTransaction;
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
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트/쿠폰 조회와 쿠폰 사용.
 *
 * <p>조회, 쿠폰 사용, 그리고 출처 기반 멱등 포인트 지급({@link #creditOnce})을
 * 담당한다. 쿠폰 발급 규칙은 아직 확정되지 않은 제품 항목이라 만들지 않는다.
 */
@Service
public class RewardService {

    private final RewardAccountRepository accountRepository;
    private final RewardTransactionRepository transactionRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public RewardService(
            RewardAccountRepository accountRepository,
            RewardTransactionRepository transactionRepository,
            CouponRepository couponRepository,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 계정 행이 없으면 0포인트로 만든다. 트랜잭션 밖에서 호출한다.
     *
     * <p>동시에 두 곳이 만들면 한쪽은 user_id unique 위반을 받는다. 이미 있다는
     * 뜻이므로 실패로 보지 않는다.
     */
    public void ensureAccount(Long userId) {
        if (accountRepository.findByUserId(userId).isPresent()) {
            return;
        }
        try {
            // 호출 시점의 트랜잭션 상태와 무관하게 독립 트랜잭션으로 만든다.
            newTransaction.executeWithoutResult(status ->
                    accountRepository.saveAndFlush(new RewardAccount(userId, 0, clock.instant())));
        } catch (DataIntegrityViolationException alreadyCreated) {
            // 다른 스레드가 먼저 만들었다.
        }
    }

    /**
     * 출처 한 건당 정확히 한 번만 포인트를 지급한다.
     *
     * <p>호출자의 트랜잭션에 합류한다(상태 전이와 지급이 함께 커밋/롤백). 이미 같은
     * 출처로 지급된 거래가 있으면 새로 지급하지 않고 그 금액을 돌려준다. 동시
     * 요청이 둘 다 확인을 통과하면 unique 인덱스가 한쪽을 DataIntegrityViolation으로
     * 막으며, 호출자는 이를 "이미 지급됨"으로 처리한다.
     *
     * @return 이 출처로 실제 지급된 포인트
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int creditOnce(
            Long userId,
            int points,
            String type,
            String description,
            String referenceType,
            Long referenceId) {
        Optional<RewardTransaction> existing =
                transactionRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
        if (existing.isPresent()) {
            return existing.get().getAmount();
        }
        if (points <= 0) {
            return 0;
        }
        Instant now = clock.instant();
        // 원장 먼저. 중복이면 여기서 unique 위반으로 잔액을 건드리기 전에 끝난다.
        transactionRepository.saveAndFlush(new RewardTransaction(
                userId, points, type, description, referenceType, referenceId, now));
        if (accountRepository.addPoints(userId, points, now) != 1) {
            // 계정은 호출 전에 ensureAccount로 만들어 둔다. 없으면 전체를 되돌린다.
            throw new IllegalStateException("리워드 계정이 없습니다.");
        }
        return points;
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

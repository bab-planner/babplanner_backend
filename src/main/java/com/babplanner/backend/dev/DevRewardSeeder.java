package com.babplanner.backend.dev;

import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.reward.domain.RewardTransaction;
import com.babplanner.backend.reward.repository.CouponRepository;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import com.babplanner.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 E2E에서 리워드 화면을 확인하기 위한 개발 fixture.
 *
 * <p>여기 숫자는 <b>개발용 예시일 뿐 실제 리워드 정책이 아니다.</b> 설문 참여
 * 시 몇 점, 잔반률 몇 퍼센트면 쿠폰 같은 규칙은 아직 확정되지 않았고 이
 * 시더가 그것을 정의하지도 않는다.
 *
 * <p>DevUserSeeder가 만든 사용자에게만 붙인다. production migration에 특정
 * 사용자 id를 박아 넣지 않기 위해 profile seeder로 둔다. 이미 데이터가 있으면
 * 아무것도 하지 않는다.
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "bapplanner.dev-user", name = "enabled", havingValue = "true")
@Order(20)
public class DevRewardSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevRewardSeeder.class);

    private final DevUserProperties properties;
    private final UserRepository userRepository;
    private final RewardAccountRepository accountRepository;
    private final RewardTransactionRepository transactionRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    public DevRewardSeeder(
            DevUserProperties properties,
            UserRepository userRepository,
            RewardAccountRepository accountRepository,
            RewardTransactionRepository transactionRepository,
            CouponRepository couponRepository,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var user = userRepository.findByPhone(properties.getPhone()).orElse(null);
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        if (accountRepository.findByUserId(userId).isPresent()) {
            // 이미 넣었다. 사용한 쿠폰이 되살아나면 안 되므로 덮어쓰지 않는다.
            return;
        }

        Instant now = clock.instant();
        accountRepository.save(new RewardAccount(userId, 100_000, now));

        // 잔액과 원장이 어긋나 보이지 않도록 합이 잔액과 같게 둔다.
        transactionRepository.save(new RewardTransaction(
                userId, 60_000, "DEV_SEED", "개발용 초기 적립", now.minus(3, ChronoUnit.DAYS)));
        transactionRepository.save(new RewardTransaction(
                userId, 40_000, "DEV_SEED", "개발용 추가 적립", now.minus(1, ChronoUnit.DAYS)));

        Instant expiresLater = now.plus(30, ChronoUnit.DAYS);
        couponRepository.save(new Coupon(
                userId, "한식 메뉴 할인 쿠폰", "10%", CouponStatus.AVAILABLE, expiresLater, now));
        couponRepository.save(new Coupon(
                userId, "한식 식사 시, 아이스크림 제공", "🍰", CouponStatus.AVAILABLE, expiresLater, now));
        couponRepository.save(new Coupon(
                userId, "전체 메뉴 할인 쿠폰", "5%", CouponStatus.AVAILABLE, null, now));
        // 이미 사용한 쿠폰과 만료된 쿠폰도 화면에서 확인할 수 있게 둔다.
        couponRepository.save(new Coupon(
                userId, "전체 메뉴 할인 쿠폰", "5%", CouponStatus.USED, expiresLater, now));
        couponRepository.save(new Coupon(
                userId,
                "한식 메뉴 할인 쿠폰",
                "10%",
                CouponStatus.AVAILABLE,
                now.minus(1, ChronoUnit.DAYS),
                now));

        log.info("개발용 리워드 fixture를 생성했습니다.");
    }
}

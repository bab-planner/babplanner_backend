package com.babplanner.backend.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.dto.AuthResponse;
import com.babplanner.backend.auth.dto.SignupRequest;
import com.babplanner.backend.auth.dto.TermsAgreementRequest;
import com.babplanner.backend.auth.service.AuthService;
import com.babplanner.backend.auth.repository.PhoneVerificationRepository;
import com.babplanner.backend.auth.repository.RefreshSessionRepository;
import com.babplanner.backend.auth.service.PhoneVerificationService;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.repository.CouponRepository;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import com.babplanner.backend.reward.service.RewardService;
import com.babplanner.backend.support.FakeSmsSender;
import com.babplanner.backend.support.TestSupportConfig;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.dto.SurveyAnswerRequest;
import com.babplanner.backend.survey.dto.SurveySubmitRequest;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.babplanner.backend.survey.repository.SurveyResponseRepository;
import com.babplanner.backend.survey.service.SurveyService;
import com.babplanner.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 같은 쿠폰/설문에 동시에 요청이 들어와도 하나만 성공해야 한다.
 *
 * <p>MockMvc 대신 서비스 계층을 여러 스레드에서 직접 호출한다. 원자적 UPDATE와
 * unique 제약이 실제로 걸려 있는지 확인하는 것이 목적이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RewardConcurrencyTest {

    @Autowired private RewardService rewardService;
    @Autowired private SurveyService surveyService;
    @Autowired private AuthService authService;
    @Autowired private PhoneVerificationService phoneVerificationService;
    @Autowired private FakeSmsSender smsSender;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshSessionRepository refreshSessionRepository;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private RewardAccountRepository rewardAccountRepository;
    @Autowired private RewardTransactionRepository rewardTransactionRepository;
    @Autowired private SurveyRepository surveyRepository;
    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private Clock clock;

    private static final String PHONE = "01012345678";
    private static final String PASSWORD = "test1234!";

    private long userId;

    @BeforeEach
    void setUp() {
        surveyResponseRepository.deleteAll();
        surveyRepository.deleteAll();
        couponRepository.deleteAll();
        rewardTransactionRepository.deleteAll();
        rewardAccountRepository.deleteAll();
        refreshSessionRepository.deleteAll();
        phoneVerificationRepository.deleteAll();
        userRepository.deleteAll();
        smsSender.clear();

        phoneVerificationService.sendCode(PHONE);
        String code = smsSender.lastCodeFor(PHONE);
        String token = phoneVerificationService.verify(PHONE, code).verificationToken();
        AuthResponse auth = authService.signup(new SignupRequest(
                "홍길동",
                PHONE,
                "20201234",
                null,
                PASSWORD,
                new TermsAgreementRequest(true, true),
                token));
        userId = auth.user().id();
    }

    /** 여러 스레드에서 같은 작업을 실행하고 성공 횟수를 센다. */
    private int countSuccesses(int threads, Runnable action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Void> task = () -> {
                    start.await();
                    try {
                        action.run();
                        succeeded.incrementAndGet();
                    } catch (RuntimeException exception) {
                        // 충돌로 실패한 쪽은 세지 않는다.
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return succeeded.get();
    }

    @Test
    @DisplayName("같은 쿠폰을 동시에 사용하면 한 번만 성공한다")
    void concurrentRedeemAllowsOnlyOne() throws Exception {
        Coupon target = couponRepository.save(new Coupon(
                userId, "한식 할인", "10%", CouponStatus.AVAILABLE, null, clock.instant()));

        int succeeded = countSuccesses(4, () -> rewardService.redeemCoupon(userId, target.getId()));

        assertThat(succeeded).isEqualTo(1);
        assertThat(couponRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("같은 설문에 동시에 제출하면 한 번만 성공한다")
    void concurrentSurveySubmitAllowsOnlyOne() throws Exception {
        Instant now = clock.instant();
        Survey survey = new Survey(
                "동시 제출 확인", null, now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)), now);
        survey.addQuestion(
                new SurveyQuestion("자유 의견", SurveyQuestionType.TEXT, false, 1, List.of()));
        Survey saved = surveyRepository.save(survey);
        Long questionId = saved.getQuestions().getFirst().getId();
        SurveySubmitRequest request = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(questionId, List.of(), null, "의견")));

        int succeeded =
                countSuccesses(4, () -> surveyService.submit(userId, saved.getId(), request));

        assertThat(succeeded).isEqualTo(1);
        assertThat(surveyResponseRepository.findAll()).hasSize(1);
    }
}

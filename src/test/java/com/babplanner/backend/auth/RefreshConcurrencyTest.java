package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.dto.AuthResponse;
import com.babplanner.backend.auth.dto.LoginRequest;
import com.babplanner.backend.auth.dto.SignupRequest;
import com.babplanner.backend.auth.dto.TermsAgreementRequest;
import com.babplanner.backend.auth.repository.PhoneVerificationRepository;
import com.babplanner.backend.auth.repository.RefreshSessionRepository;
import com.babplanner.backend.auth.service.AuthService;
import com.babplanner.backend.auth.service.PhoneVerificationService;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.support.FakeSmsSender;
import com.babplanner.backend.support.TestSupportConfig;
import com.babplanner.backend.user.repository.UserRepository;
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
 * 같은 refresh token으로 동시에 재발급을 시도해도 하나만 성공해야 한다.
 *
 * <p>MockMvc 대신 서비스 계층을 직접 여러 스레드에서 호출한다. 회전이
 * revokeIfActive의 원자적 UPDATE에 걸려 있는지 확인하는 것이 목적이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RefreshConcurrencyTest {

    @Autowired private AuthService authService;
    @Autowired private PhoneVerificationService phoneVerificationService;
    @Autowired private FakeSmsSender smsSender;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshSessionRepository refreshSessionRepository;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;

    private static final String PHONE = "01012345678";
    private static final String PASSWORD = "test1234!";

    @BeforeEach
    void setUp() {
        refreshSessionRepository.deleteAll();
        phoneVerificationRepository.deleteAll();
        userRepository.deleteAll();
        smsSender.clear();

        phoneVerificationService.sendCode(PHONE);
        String code = smsSender.lastCodeFor(PHONE);
        String token = phoneVerificationService.verify(PHONE, code).verificationToken();
        authService.signup(new SignupRequest(
                "홍길동",
                PHONE,
                "20201234",
                null,
                PASSWORD,
                new TermsAgreementRequest(true, true),
                token));
    }

    @Test
    @DisplayName("같은 refresh token으로 동시에 재발급하면 한 번만 성공한다")
    void concurrentRefreshAllowsOnlyOneWinner() throws Exception {
        AuthResponse login = authService.login(new LoginRequest(PHONE, PASSWORD));
        String refreshToken = login.refreshToken();

        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Void> task = () -> {
                    start.await();
                    try {
                        authService.refresh(refreshToken);
                        succeeded.incrementAndGet();
                    } catch (ApiException exception) {
                        rejected.incrementAndGet();
                    } catch (RuntimeException exception) {
                        // 낙관적 충돌로 DB 예외가 나도 재발급 실패로 센다.
                        rejected.incrementAndGet();
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

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
    }
}

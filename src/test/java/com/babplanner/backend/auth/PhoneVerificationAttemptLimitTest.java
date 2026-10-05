package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.domain.PhoneVerification;
import com.babplanner.backend.auth.service.PhoneVerificationService;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 인증번호 무차별 대입 제한(기본 5회). */
class PhoneVerificationAttemptLimitTest extends ApiIntegrationTest {

    @Autowired private PhoneVerificationService verificationService;

    private MvcResult verify(String code) throws Exception {
        return postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", code));
    }

    private String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private int attempts() {
        return phoneVerificationRepository
                .findFirstByPhoneOrderByIdDesc(PHONE)
                .map(PhoneVerification::getFailedAttempts)
                .orElseThrow();
    }

    @Test
    @DisplayName("5번 틀리면 이후에는 맞는 코드도 거절된다")
    void locksAfterFiveWrongAttempts() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String code = smsSender.lastCodeFor(PHONE);

        for (int i = 0; i < 5; i++) {
            assertThat(verify(wrongCode(code)).getResponse().getStatus()).isEqualTo(400);
        }
        MvcResult correct = verify(code);

        assertThat(correct.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(correct).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_CODE");
        assertThat(attempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("4번 틀린 뒤 맞으면 성공한다")
    void succeedsWithinLimit() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String code = smsSender.lastCodeFor(PHONE);
        for (int i = 0; i < 4; i++) {
            verify(wrongCode(code));
        }

        MvcResult correct = verify(code);

        assertThat(correct.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(correct).path("data").path("verificationToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("틀릴 때마다 횟수가 DB에 남는다")
    void countsArePersisted() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String code = smsSender.lastCodeFor(PHONE);

        verify(wrongCode(code));
        verify(wrongCode(code));

        assertThat(attempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 틀려도 허용 횟수를 넘지 않는다")
    void concurrentWrongAttemptsAreCapped() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String wrong = wrongCode(smsSender.lastCodeFor(PHONE));
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    verificationService.verify(PHONE, wrong);
                    return true;
                } catch (ApiException expected) {
                    assertThat(expected.errorCode()).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
                    return false;
                }
            }));
        }
        start.countDown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get(30, TimeUnit.SECONDS)).isFalse();
        }
        pool.shutdown();

        assertThat(attempts()).isEqualTo(5);
    }
}

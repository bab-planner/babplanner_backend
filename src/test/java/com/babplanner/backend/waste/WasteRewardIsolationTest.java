package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 잔반 인증이 리워드를 건드리지 않는지 확인한다.
 *
 * <p>APPROVED는 "분석이 끝났다"는 뜻이지 "지급했다"는 뜻이 아니다. 지급 규칙이
 * 정해지기 전에 포인트나 쿠폰이 움직이면 안 된다.
 */
class WasteRewardIsolationTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/waste-submissions";

    @Autowired private MenuRepository menuRepository;

    private void submit(JsonNode auth) throws Exception {
        perform(multipart(PATH)
                .file(new MockMultipartFile(
                        "image",
                        "lunch.jpg",
                        "image/jpeg",
                        "fake-image".getBytes(StandardCharsets.UTF_8)))
                .param("menuId", String.valueOf(menuRepository.findAll().getFirst().getId()))
                .param("capturedAt", "2026-08-26T03:00:00Z")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));
    }

    @Test
    @DisplayName("포인트 잔액이 변하지 않는다")
    void pointsUnchanged() throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();
        rewardAccountRepository.save(new RewardAccount(userId, 1234, clock.instant()));

        submit(auth);

        assertThat(rewardAccountRepository.findAll().getFirst().getPoints()).isEqualTo(1234);
    }

    @Test
    @DisplayName("쿠폰이 새로 생기지 않는다")
    void noCouponIssued() throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();
        couponRepository.save(new Coupon(
                userId, "한식 할인", "10%", CouponStatus.AVAILABLE, null, clock.instant()));

        submit(auth);

        assertThat(couponRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("리워드 거래 내역이 생기지 않는다")
    void noRewardTransaction() throws Exception {
        JsonNode auth = signup();

        submit(auth);

        assertThat(rewardTransactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("계정이 없던 사용자에게 계정이 만들어지지 않는다")
    void noAccountCreated() throws Exception {
        JsonNode auth = signup();

        submit(auth);

        assertThat(rewardAccountRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("리워드 요약 응답이 그대로다")
    void rewardSummaryUnchanged() throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();
        rewardAccountRepository.save(new RewardAccount(userId, 500, clock.instant()));
        JsonNode before =
                body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");

        submit(auth);

        JsonNode after = body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");
        assertThat(after.path("points").asInt()).isEqualTo(before.path("points").asInt());
        assertThat(after.path("availableCouponCount").asInt())
                .isEqualTo(before.path("availableCouponCount").asInt());
    }
}

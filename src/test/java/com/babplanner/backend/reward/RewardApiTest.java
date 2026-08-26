package com.babplanner.backend.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.reward.domain.RewardTransaction;
import com.babplanner.backend.reward.repository.CouponRepository;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class RewardApiTest extends ApiIntegrationTest {

    @Autowired private RewardAccountRepository accountRepository;
    @Autowired private RewardTransactionRepository transactionRepository;
    @Autowired private CouponRepository couponRepository;

    private long userIdOf(JsonNode auth) {
        return auth.path("user").path("id").asLong();
    }

    private Coupon coupon(long userId, String title, String badge, CouponStatus status, Instant expiresAt) {
        return couponRepository.save(
                new Coupon(userId, title, badge, status, expiresAt, clock.instant()));
    }

    @Test
    @DisplayName("summary는 인증이 필요하다")
    void summaryRequiresAuth() throws Exception {
        assertThat(getJson("/api/v1/rewards/summary").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("리워드가 없으면 0포인트 0장이다")
    void emptyReward() throws Exception {
        JsonNode auth = signup();

        JsonNode data =
                body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");

        assertThat(data.path("points").asInt()).isZero();
        assertThat(data.path("availableCouponCount").asInt()).isZero();
    }

    @Test
    @DisplayName("포인트와 사용 가능 쿠폰 수를 계약 키로 돌려준다")
    void summaryReturnsPointsAndCount() throws Exception {
        JsonNode auth = signup();
        long userId = userIdOf(auth);
        Instant now = clock.instant();
        accountRepository.save(new RewardAccount(userId, 100_000, now));
        coupon(userId, "쿠폰A", "10%", CouponStatus.AVAILABLE, now.plusSeconds(3600));
        coupon(userId, "쿠폰B", null, CouponStatus.USED, null);
        // 만료된 쿠폰은 사용 가능 수에 넣지 않는다.
        coupon(userId, "쿠폰C", "5%", CouponStatus.AVAILABLE, now.minusSeconds(10));

        JsonNode data =
                body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");

        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("points", "availableCouponCount");
        assertThat(data.path("points").asInt()).isEqualTo(100_000);
        assertThat(data.path("availableCouponCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("transactions는 인증이 필요하다")
    void transactionsRequireAuth() throws Exception {
        assertThat(getJson("/api/v1/rewards/transactions").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("내역을 최신순으로 돌려준다")
    void transactionsAreNewestFirst() throws Exception {
        JsonNode auth = signup();
        long userId = userIdOf(auth);
        Instant now = clock.instant();
        transactionRepository.save(
                new RewardTransaction(userId, 100, "EARN", "오래된", now.minusSeconds(600)));
        transactionRepository.save(
                new RewardTransaction(userId, 200, "EARN", "최신", now.minusSeconds(60)));

        JsonNode data =
                body(getJson("/api/v1/rewards/transactions", accessTokenOf(auth))).path("data");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("description").asText()).isEqualTo("최신");
        assertThat(data.get(0).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "amount", "type", "description", "createdAt");
    }

    @Test
    @DisplayName("알 수 없는 type도 문자열로 그대로 저장/반환한다")
    void transactionTypeIsFreeString() throws Exception {
        JsonNode auth = signup();
        transactionRepository.save(new RewardTransaction(
                userIdOf(auth), -50, "SEASONAL_BONUS_2027", null, clock.instant()));

        JsonNode item =
                body(getJson("/api/v1/rewards/transactions", accessTokenOf(auth)))
                        .path("data")
                        .get(0);

        assertThat(item.path("type").asText()).isEqualTo("SEASONAL_BONUS_2027");
        assertThat(item.path("description").isNull()).isTrue();
    }

    @Test
    @DisplayName("coupons는 인증이 필요하다")
    void couponsRequireAuth() throws Exception {
        assertThat(getJson("/api/v1/rewards/coupons").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("쿠폰을 계약 키로 돌려준다")
    void couponKeysMatchContract() throws Exception {
        JsonNode auth = signup();
        coupon(userIdOf(auth), "한식 할인", "10%", CouponStatus.AVAILABLE, clock.instant().plusSeconds(600));

        JsonNode item =
                body(getJson("/api/v1/rewards/coupons", accessTokenOf(auth))).path("data").get(0);

        assertThat(item.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "title", "status", "badgeLabel", "expiresAt");
        assertThat(item.path("badgeLabel").asText()).isEqualTo("10%");
        assertThat(item.path("status").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("badgeLabel과 expiresAt은 null일 수 있다")
    void nullableCouponFields() throws Exception {
        JsonNode auth = signup();
        coupon(userIdOf(auth), "배지 없는 쿠폰", null, CouponStatus.AVAILABLE, null);

        JsonNode item =
                body(getJson("/api/v1/rewards/coupons", accessTokenOf(auth))).path("data").get(0);

        assertThat(item.has("badgeLabel")).isTrue();
        assertThat(item.path("badgeLabel").isNull()).isTrue();
        assertThat(item.path("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("만료된 쿠폰은 EXPIRED로 표시된다")
    void expiredCouponShowsExpired() throws Exception {
        JsonNode auth = signup();
        coupon(userIdOf(auth), "만료 쿠폰", "5%", CouponStatus.AVAILABLE, clock.instant().minusSeconds(1));

        JsonNode item =
                body(getJson("/api/v1/rewards/coupons", accessTokenOf(auth))).path("data").get(0);

        assertThat(item.path("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("사용 가능한 쿠폰이 먼저 온다")
    void availableCouponsComeFirst() throws Exception {
        JsonNode auth = signup();
        long userId = userIdOf(auth);
        coupon(userId, "사용됨", null, CouponStatus.USED, null);
        coupon(userId, "사용가능", null, CouponStatus.AVAILABLE, null);

        JsonNode data = body(getJson("/api/v1/rewards/coupons", accessTokenOf(auth))).path("data");

        assertThat(data.get(0).path("status").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("실제 쿠폰 id로 사용하고 서버 상태를 돌려준다")
    void redeemUsesActualId() throws Exception {
        JsonNode auth = signup();
        Coupon target = coupon(
                userIdOf(auth), "한식 할인", "10%", CouponStatus.AVAILABLE, clock.instant().plusSeconds(600));

        MvcResult result = postJson(
                "/api/v1/rewards/coupons/" + target.getId() + "/redeem", null, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(target.getId());
        assertThat(data.path("status").asText()).isEqualTo("USED");
        assertThat(couponRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("쿠폰 사용이 포인트를 깎지 않는다")
    void redeemDoesNotSpendPoints() throws Exception {
        JsonNode auth = signup();
        long userId = userIdOf(auth);
        accountRepository.save(new RewardAccount(userId, 100_000, clock.instant()));
        Coupon target = coupon(userId, "한식 할인", "10%", CouponStatus.AVAILABLE, null);

        postJson("/api/v1/rewards/coupons/" + target.getId() + "/redeem", null, accessTokenOf(auth));

        assertThat(accountRepository.findByUserId(userId).orElseThrow().getPoints())
                .isEqualTo(100_000);
        assertThat(transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)).isEmpty();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 409다")
    void usedCouponConflicts() throws Exception {
        JsonNode auth = signup();
        Coupon target = coupon(userIdOf(auth), "사용됨", null, CouponStatus.USED, null);

        MvcResult result = postJson(
                "/api/v1/rewards/coupons/" + target.getId() + "/redeem", null, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("COUPON_NOT_USABLE");
    }

    @Test
    @DisplayName("만료된 쿠폰은 409다")
    void expiredCouponConflicts() throws Exception {
        JsonNode auth = signup();
        Coupon target = coupon(
                userIdOf(auth), "만료됨", null, CouponStatus.AVAILABLE, clock.instant().plusSeconds(60));

        clock.advance(Duration.ofMinutes(2));
        MvcResult result = postJson(
                "/api/v1/rewards/coupons/" + target.getId() + "/redeem", null, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(couponRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("남의 쿠폰은 404이며 존재를 알리지 않는다")
    void foreignCouponIsNotFound() throws Exception {
        JsonNode owner = signup();
        Coupon target = coupon(userIdOf(owner), "남의 쿠폰", "10%", CouponStatus.AVAILABLE, null);
        JsonNode other = signup("01098765432", "20205678");

        MvcResult result = postJson(
                "/api/v1/rewards/coupons/" + target.getId() + "/redeem", null, accessTokenOf(other));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("남의 쿠폰");
        assertThat(couponRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰은 목록에 보이지 않는다")
    void couponsAreScopedToUser() throws Exception {
        JsonNode owner = signup();
        coupon(userIdOf(owner), "내 쿠폰", null, CouponStatus.AVAILABLE, null);
        JsonNode other = signup("01098765432", "20205678");

        assertThat(body(getJson("/api/v1/rewards/coupons", accessTokenOf(other))).path("data"))
                .isEmpty();
    }

    @Test
    @DisplayName("없는 쿠폰은 404다")
    void unknownCouponIsNotFound() throws Exception {
        JsonNode auth = signup();

        assertThat(
                        postJson("/api/v1/rewards/coupons/999999/redeem", null, accessTokenOf(auth))
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("요청에 userId를 넣어도 남의 리워드를 보지 못한다")
    void userIdInQueryIsIgnored() throws Exception {
        JsonNode owner = signup();
        accountRepository.save(new RewardAccount(userIdOf(owner), 100_000, clock.instant()));
        JsonNode other = signup("01098765432", "20205678");

        JsonNode data = body(getJson(
                        "/api/v1/rewards/summary?userId=" + userIdOf(owner), accessTokenOf(other)))
                .path("data");

        assertThat(data.path("points").asInt()).isZero();
    }

    @Test
    @DisplayName("응답이 success/data/error 세 키를 갖는다")
    void envelopeShape() throws Exception {
        JsonNode auth = signup();
        List<String> paths = new ArrayList<>(
                List.of("/api/v1/rewards/summary", "/api/v1/rewards/transactions", "/api/v1/rewards/coupons"));

        for (String path : paths) {
            JsonNode json = body(getJson(path, accessTokenOf(auth)));
            assertThat(json.fieldNames())
                    .as("%s", path)
                    .toIterable()
                    .containsExactlyInAnyOrder("success", "data", "error");
        }
    }
}

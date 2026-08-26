package com.babplanner.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.inquiry.domain.InquiryCategory;
import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Flutter Stage 8/6/10 RealRepository parser가 기대하는 shape와 맞는지 확인한다.
 *
 * <p>기대값은 Flutter 소스(reward_models.dart / survey.dart / mypage_models.dart)의
 * fromJson을 직접 읽어 옮긴 것이다.
 */
class Stage16ContractTest extends ApiIntegrationTest {

    @Autowired private SurveyRepository surveyRepository;

    @Test
    @DisplayName("CouponStatus 문자열이 Flutter enum과 같다")
    void couponStatusStringsMatch() {
        assertThat(Arrays.stream(CouponStatus.values()).map(Enum::name).toList())
                .containsExactly("AVAILABLE", "USED", "EXPIRED");
    }

    @Test
    @DisplayName("SurveyQuestionType 문자열이 Flutter enum과 같다")
    void surveyQuestionTypeStringsMatch() {
        assertThat(Arrays.stream(SurveyQuestionType.values()).map(Enum::name).toList())
                .containsExactly("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SCALE", "TEXT");
    }

    @Test
    @DisplayName("InquiryCategory 문자열이 Flutter enum과 같다")
    void inquiryCategoryStringsMatch() {
        assertThat(Arrays.stream(InquiryCategory.values()).map(Enum::name).toList())
                .containsExactly("MENU", "ALLERGY", "FACILITY", "SERVICE", "OTHER");
    }

    @Test
    @DisplayName("InquiryStatus 문자열이 Flutter enum과 같다")
    void inquiryStatusStringsMatch() {
        assertThat(Arrays.stream(InquiryStatus.values()).map(Enum::name).toList())
                .containsExactly("RECEIVED", "IN_PROGRESS", "ANSWERED", "CLOSED");
    }

    @Test
    @DisplayName("Reward 응답이 Flutter parser가 읽는 키를 제공한다")
    void rewardProvidesParserKeys() throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();
        Instant now = clock.instant();
        rewardAccountRepository.save(new RewardAccount(userId, 1234, now));
        couponRepository.save(new Coupon(
                userId, "한식 할인", "10%", CouponStatus.AVAILABLE, now.plus(Duration.ofDays(1)), now));
        rewardTransactionRepository.save(new com.babplanner.backend.reward.domain.RewardTransaction(
                userId, 100, "EARN", "적립", now));

        JsonNode summary =
                body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");
        assertThat(summary.path("points").isNumber()).isTrue();
        assertThat(summary.path("availableCouponCount").isNumber()).isTrue();

        JsonNode coupon =
                body(getJson("/api/v1/rewards/coupons", accessTokenOf(auth))).path("data").get(0);
        // RewardCoupon.fromJson이 non-null로 읽는 필드
        assertThat(coupon.path("id").isNumber()).isTrue();
        assertThat(coupon.path("title").isTextual()).isTrue();
        assertThat(coupon.path("status").isTextual()).isTrue();
        // nullable로 읽는 필드도 키가 있어야 한다.
        assertThat(coupon.has("badgeLabel")).isTrue();
        assertThat(coupon.has("expiresAt")).isTrue();

        JsonNode transaction = body(getJson("/api/v1/rewards/transactions", accessTokenOf(auth)))
                .path("data")
                .get(0);
        assertThat(transaction.path("id").isNumber()).isTrue();
        assertThat(transaction.path("amount").isNumber()).isTrue();
        assertThat(transaction.path("type").isTextual()).isTrue();
        assertThat(transaction.has("description")).isTrue();
        assertThat(java.time.Instant.parse(transaction.path("createdAt").asText())).isNotNull();
    }

    @Test
    @DisplayName("Survey 응답이 Flutter parser가 읽는 키를 제공한다")
    void surveyProvidesParserKeys() throws Exception {
        Instant now = clock.instant();
        Survey survey = new Survey(
                "제목", "설명", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)), now);
        survey.addQuestion(new SurveyQuestion(
                "만족도", SurveyQuestionType.SCALE, true, 1, List.of("매우 만족", "만족")));
        surveyRepository.save(survey);
        JsonNode auth = signup();

        JsonNode data = body(getJson("/api/v1/surveys/active", accessTokenOf(auth))).path("data");

        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("title").isTextual()).isTrue();
        assertThat(data.has("description")).isTrue();
        assertThat(java.time.Instant.parse(data.path("startsAt").asText())).isNotNull();
        assertThat(java.time.Instant.parse(data.path("endsAt").asText())).isNotNull();

        JsonNode question = data.path("questions").get(0);
        assertThat(question.path("id").isNumber()).isTrue();
        assertThat(question.path("text").isTextual()).isTrue();
        assertThat(question.path("type").isTextual()).isTrue();
        // 계약 키는 required다. Java 필드명(isRequired)이 새어 나오면 안 된다.
        assertThat(question.has("required")).isTrue();
        assertThat(question.has("isRequired")).isFalse();
        assertThat(question.path("options").isArray()).isTrue();
    }

    @Test
    @DisplayName("Inquiry 응답이 Flutter parser가 읽는 키를 제공한다")
    void inquiryProvidesParserKeys() throws Exception {
        JsonNode auth = signup();

        JsonNode data = body(postJson(
                        "/api/v1/inquiries",
                        Map.of("category", "MENU", "title", "제목", "content", "본문"),
                        accessTokenOf(auth)))
                .path("data");

        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("category").isTextual()).isTrue();
        assertThat(data.path("title").isTextual()).isTrue();
        assertThat(data.path("content").isTextual()).isTrue();
        assertThat(data.path("status").isTextual()).isTrue();
        assertThat(data.has("adminAnswer")).isTrue();
        assertThat(data.has("answeredAt")).isTrue();
        assertThat(java.time.Instant.parse(data.path("createdAt").asText())).isNotNull();
    }

    @Test
    @DisplayName("신규 endpoint가 모두 envelope 세 키를 갖는다")
    void envelopeShapeAcrossNewEndpoints() throws Exception {
        JsonNode auth = signup();

        for (String path : List.of(
                "/api/v1/rewards/summary",
                "/api/v1/rewards/transactions",
                "/api/v1/rewards/coupons",
                "/api/v1/surveys/active",
                "/api/v1/inquiries")) {
            JsonNode json = body(getJson(path, accessTokenOf(auth)));
            assertThat(json.fieldNames())
                    .as("%s", path)
                    .toIterable()
                    .containsExactlyInAnyOrder("success", "data", "error");
        }
    }

    @Test
    @DisplayName("익명 접근은 모두 401이다")
    void newEndpointsRequireAuth() throws Exception {
        for (String path : List.of(
                "/api/v1/rewards/summary",
                "/api/v1/rewards/transactions",
                "/api/v1/rewards/coupons",
                "/api/v1/surveys/active",
                "/api/v1/inquiries")) {
            assertThat(getJson(path).getResponse().getStatus()).as("%s", path).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("Auth/User/Menu/Notice 회귀가 없다")
    void existingFeaturesStillWork() throws Exception {
        JsonNode auth = signup();

        assertThat(getJson("/api/v1/users/me", accessTokenOf(auth)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(getJson("/api/v1/allergies").getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/menus?date=2026-08-26").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(getJson("/api/v1/notices").getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/congestion/current").getResponse().getStatus()).isEqualTo(200);
    }
}

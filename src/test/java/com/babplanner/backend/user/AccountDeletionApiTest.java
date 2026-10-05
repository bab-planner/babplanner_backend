package com.babplanner.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.allergy.domain.UserAllergy;
import com.babplanner.backend.allergy.repository.AllergyRepository;
import com.babplanner.backend.allergy.repository.UserAllergyRepository;
import com.babplanner.backend.inquiry.domain.Inquiry;
import com.babplanner.backend.inquiry.domain.InquiryCategory;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.reward.domain.RewardTransaction;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyAnswer;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.domain.SurveyResponse;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** POST /api/v1/users/me/deletion. */
class AccountDeletionApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/users/me/deletion";
    private static final String OTHER_PHONE = "01098765432";
    private static final String OTHER_STUDENT_ID = "20205678";
    private static final byte[] IMAGE = "img".getBytes();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SurveyRepository surveyRepository;
    @Autowired private AllergyRepository allergyRepository;
    @Autowired private UserAllergyRepository userAllergyRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private WasteImageStorage imageStorage;

    @AfterEach
    void cleanSurveys() {
        surveyResponseRepository.deleteAll();
        surveyRepository.deleteAll();
    }

    private String storeImage() {
        return imageStorage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE), "a.jpg", "image/jpeg", IMAGE.length))
                .storageKey();
    }

    /** 모든 테이블에 이 사용자의 행을 하나씩 만들고 이미지 키를 돌려준다. */
    private String seedData(long userId, Survey survey) {
        Instant now = clock.instant();
        SurveyResponse response = new SurveyResponse(survey.getId(), userId, now);
        Long questionId = survey.getQuestions().getFirst().getId();
        response.addAnswer(new SurveyAnswer(questionId, List.of("A", "B"), null, null));
        surveyResponseRepository.save(response);

        String key = storeImage();
        wasteSubmissionRepository.save(new WasteSubmission(
                userId, menuRepository.findAll().getFirst().getId(), key, now, now));
        inquiryRepository.save(new Inquiry(userId, InquiryCategory.values()[0], "t", "c", now));
        couponRepository.save(new Coupon(userId, "쿠폰", "10%", CouponStatus.AVAILABLE, null, now));
        rewardAccountRepository.save(new RewardAccount(userId, 100, now));
        rewardTransactionRepository.save(new RewardTransaction(userId, 100, "T", "d", now));
        userAllergyRepository.save(
                new UserAllergy(userId, allergyRepository.findAll().getFirst().getId(), now));
        return key;
    }

    private Survey newSurvey() {
        Instant now = clock.instant();
        Survey survey = new Survey(
                "탈퇴 테스트", null, now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)), now);
        survey.addQuestion(new SurveyQuestion(
                "선택", SurveyQuestionType.MULTIPLE_CHOICE, false, 1, List.of("A", "B", "C")));
        return surveyRepository.save(survey);
    }

    private long count(String table, String column, Object value) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Long.class, value);
    }

    private static final List<String> USER_TABLES = List.of(
            "survey_responses",
            "waste_submissions",
            "inquiries",
            "coupons",
            "reward_transactions",
            "reward_accounts",
            "user_allergies",
            "refresh_sessions");

    private MvcResult delete(JsonNode auth, String password) throws Exception {
        return postJson(PATH, Map.of("password", password), accessTokenOf(auth));
    }

    @Test
    @DisplayName("탈퇴하면 그 사용자의 모든 데이터만 지워지고 이미지도 지워진다")
    void deletesOnlyThatUsersData() throws Exception {
        JsonNode a = signup();
        JsonNode b = signup(OTHER_PHONE, OTHER_STUDENT_ID);
        long aId = a.path("user").path("id").asLong();
        long bId = b.path("user").path("id").asLong();
        Survey survey = newSurvey();
        String aKey = seedData(aId, survey);
        String bKey = seedData(bId, survey);
        long optionsBefore = jdbc.queryForObject(
                "select count(*) from survey_answer_options", Long.class);
        assertThat(optionsBefore).isEqualTo(4);

        MvcResult result = delete(a, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(userRepository.findById(aId)).isEmpty();
        for (String table : USER_TABLES) {
            assertThat(count(table, "user_id", aId)).as(table + " (A)").isZero();
            assertThat(count(table, "user_id", bId)).as(table + " (B)").isEqualTo(1);
        }
        assertThat(count("phone_verifications", "phone", PHONE)).isZero();
        assertThat(count("phone_verifications", "phone", OTHER_PHONE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from survey_answer_options", Long.class))
                .isEqualTo(2);
        assertThat(userRepository.findById(bId)).isPresent();
        assertThat(imageStorage.exists(aKey)).isFalse();
        assertThat(imageStorage.exists(bKey)).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400 INVALID_PASSWORD이고 아무것도 지워지지 않는다")
    void wrongPassword() throws Exception {
        JsonNode a = signup();
        long aId = a.path("user").path("id").asLong();
        String key = seedData(aId, newSurvey());

        MvcResult result = delete(a, "wrong-password");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INVALID_PASSWORD");
        assertThat(userRepository.findById(aId)).isPresent();
        assertThat(count("waste_submissions", "user_id", aId)).isEqualTo(1);
        assertThat(imageStorage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("인증 없이는 401이다")
    void unauthenticated() throws Exception {
        MvcResult result = postJson(PATH, Map.of("password", PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("관리자는 403이고 계정이 남는다")
    void adminForbidden() throws Exception {
        JsonNode admin = adminAuth();

        MvcResult result = delete(admin, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(userRepository.findByPhone(ADMIN_PHONE)).isPresent();
    }

    @Test
    @DisplayName("탈퇴 후 refresh와 access token이 모두 거절된다")
    void tokensStopWorking() throws Exception {
        JsonNode a = signup();
        delete(a, PASSWORD);

        MvcResult refresh = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", a.path("refreshToken").asText()));
        MvcResult me = getJson("/api/v1/users/me", accessTokenOf(a));

        assertThat(refresh.getResponse().getStatus()).isEqualTo(401);
        assertThat(me.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("같은 번호와 학번으로 다시 가입할 수 있다")
    void canSignUpAgain() throws Exception {
        JsonNode a = signup();
        delete(a, PASSWORD);

        JsonNode again = signup();

        assertThat(again.path("user").path("id").asLong()).isPositive();
        assertThat(accessTokenOf(again)).isNotBlank();
    }

    @Test
    @DisplayName("두 번째 호출은 사용자가 없어 401이다")
    void secondCallIsUnauthorized() throws Exception {
        JsonNode a = signup();
        assertThat(delete(a, PASSWORD).getResponse().getStatus()).isEqualTo(204);

        assertThat(delete(a, PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("검토 대기/승인된 제출이 있어도 탈퇴되고 이미지가 지워진다")
    void deletesUserWithPendingAndReviewedSubmissions() throws Exception {
        JsonNode a = signup();
        JsonNode admin = adminAuth();
        long aId = a.path("user").path("id").asLong();
        long adminId = admin.path("user").path("id").asLong();
        Instant now = clock.instant();
        long menuId = menuRepository.findAll().getFirst().getId();
        String pendingKey = storeImage();
        String approvedKey = storeImage();
        wasteSubmissionRepository.save(new WasteSubmission(aId, menuId, pendingKey, now, now));
        WasteSubmission approved =
                new WasteSubmission(aId, menuId, approvedKey, now, now);
        approved.markReviewed(
                com.babplanner.backend.waste.domain.WasteSubmissionStatus.APPROVED, 100, adminId, now);
        wasteSubmissionRepository.save(approved);
        rewardAccountRepository.save(new RewardAccount(aId, 100, now));
        rewardTransactionRepository.save(new RewardTransaction(
                aId, 100, "WASTE_APPROVED", "잔반 인증 적립", "WASTE_SUBMISSION", approved.getId(), now));

        MvcResult result = delete(a, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(count("waste_submissions", "user_id", aId)).isZero();
        assertThat(count("reward_transactions", "user_id", aId)).isZero();
        assertThat(imageStorage.exists(pendingKey)).isFalse();
        assertThat(imageStorage.exists(approvedKey)).isFalse();
        assertThat(userRepository.findById(adminId)).isPresent();
    }

    @Test
    @DisplayName("이미지 삭제가 실패해도 탈퇴는 성공한다")
    void imageDeletionFailureDoesNotFailDeletion() throws Exception {
        JsonNode a = signup();
        long aId = a.path("user").path("id").asLong();
        Instant now = clock.instant();
        // 저장소 root 밖을 가리키는 키. 삭제가 거부되어도 탈퇴에는 영향이 없어야 한다.
        wasteSubmissionRepository.save(new WasteSubmission(
                aId, menuRepository.findAll().getFirst().getId(), "../escape", now, now));

        MvcResult result = delete(a, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(userRepository.findById(aId)).isEmpty();
        assertThat(count("waste_submissions", "user_id", aId)).isZero();
    }
}

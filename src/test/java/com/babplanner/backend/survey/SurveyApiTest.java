package com.babplanner.backend.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class SurveyApiTest extends ApiIntegrationTest {

    @Autowired private SurveyRepository surveyRepository;

    private Survey survey;

    @BeforeEach
    void seedSurvey() {
        surveyRepository.deleteAll();
        Instant now = clock.instant();
        Survey created = new Survey(
                "오늘의 식사 만족도",
                "설명",
                now.minus(Duration.ofHours(1)),
                now.plus(Duration.ofDays(1)),
                now);
        created.addQuestion(new SurveyQuestion(
                "만족도는?",
                SurveyQuestionType.SCALE,
                true,
                1,
                List.of("매우 만족", "만족", "보통", "불만족", "매우 불만족")));
        created.addQuestion(new SurveyQuestion(
                "가장 좋았던 메뉴는?",
                SurveyQuestionType.SINGLE_CHOICE,
                true,
                2,
                List.of("제육덮밥", "비빔밥")));
        created.addQuestion(new SurveyQuestion(
                "개선점은?", SurveyQuestionType.MULTIPLE_CHOICE, false, 3, List.of("맛", "양")));
        created.addQuestion(
                new SurveyQuestion("자유 의견", SurveyQuestionType.TEXT, false, 4, List.of()));
        survey = surveyRepository.save(created);
    }

    private List<Long> questionIds() {
        List<Long> ids = new ArrayList<>();
        surveyRepository.findWithQuestionsById(survey.getId()).orElseThrow().getQuestions()
                .forEach(question -> ids.add(question.getId()));
        return ids;
    }

    private Map<String, Object> validAnswers() {
        List<Long> ids = questionIds();
        return Map.of(
                "answers",
                List.of(
                        Map.of("questionId", ids.get(0), "scaleValue", 5),
                        Map.of("questionId", ids.get(1), "selectedOptions", List.of("제육덮밥")),
                        Map.of("questionId", ids.get(2), "selectedOptions", List.of("맛", "양")),
                        Map.of("questionId", ids.get(3), "textValue", "맛있었어요")));
    }

    @Test
    @DisplayName("active는 인증이 필요하다")
    void activeRequiresAuth() throws Exception {
        assertThat(getJson("/api/v1/surveys/active").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("활성 설문을 계약 키로 돌려준다")
    void activeReturnsSurvey() throws Exception {
        JsonNode auth = signup();

        JsonNode data = body(getJson("/api/v1/surveys/active", accessTokenOf(auth))).path("data");

        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id", "title", "description", "startsAt", "endsAt", "questions");
        assertThat(data.path("questions")).hasSize(4);
    }

    @Test
    @DisplayName("질문 4종 type 문자열이 계약과 같다")
    void questionTypesMatchContract() throws Exception {
        JsonNode auth = signup();

        JsonNode questions =
                body(getJson("/api/v1/surveys/active", accessTokenOf(auth)))
                        .path("data")
                        .path("questions");

        List<String> types = new ArrayList<>();
        questions.forEach(question -> types.add(question.path("type").asText()));
        assertThat(types)
                .containsExactly("SCALE", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TEXT");
        assertThat(questions.get(0).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "text", "type", "required", "options");
        assertThat(questions.get(0).path("required").asBoolean()).isTrue();
        assertThat(questions.get(2).path("required").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("기간이 지난 설문은 활성이 아니다")
    void expiredSurveyIsNotActive() throws Exception {
        // 시계를 밀면 access token까지 만료되므로, 이미 끝난 설문을 직접 넣는다.
        surveyRepository.deleteAll();
        Instant now = clock.instant();
        Survey past = new Survey(
                "이미 끝난 설문",
                null,
                now.minus(Duration.ofDays(10)),
                now.minus(Duration.ofDays(1)),
                now);
        past.addQuestion(
                new SurveyQuestion("질문", SurveyQuestionType.TEXT, false, 1, List.of()));
        surveyRepository.save(past);
        JsonNode auth = signup();

        JsonNode json = body(getJson("/api/v1/surveys/active", accessTokenOf(auth)));

        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("활성 설문이 없으면 data가 null이다")
    void noActiveSurveyReturnsNullData() throws Exception {
        surveyRepository.deleteAll();
        JsonNode auth = signup();

        MvcResult result = getJson("/api/v1/surveys/active", accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("정상 제출은 201이고 본문이 없다")
    void submitSucceeds() throws Exception {
        JsonNode auth = signup();

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(surveyResponseRepository.existsByUserIdAndSurveyId(
                        auth.path("user").path("id").asLong(), survey.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("제출에는 인증이 필요하다")
    void submitRequiresAuth() throws Exception {
        MvcResult result =
                postJson("/api/v1/surveys/" + survey.getId() + "/responses", validAnswers());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("중복 제출은 409다")
    void duplicateSubmitConflicts() throws Exception {
        JsonNode auth = signup();
        postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(auth));

        MvcResult second = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(auth));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(second).path("error").path("code").asText())
                .isEqualTo("SURVEY_ALREADY_SUBMITTED");
    }

    @Test
    @DisplayName("다른 사용자는 같은 설문에 참여할 수 있다")
    void differentUsersCanSubmit() throws Exception {
        JsonNode first = signup();
        postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(first));
        JsonNode second = signup("01098765432", "20205678");

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(second));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("다른 설문의 질문 id는 거부한다")
    void foreignQuestionIdRejected() throws Exception {
        JsonNode auth = signup();
        Map<String, Object> payload =
                Map.of("answers", List.of(Map.of("questionId", 999_999, "scaleValue", 5)));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_SURVEY_ANSWER");
    }

    @Test
    @DisplayName("필수 질문을 빠뜨리면 거부한다")
    void missingRequiredQuestionRejected() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = questionIds();
        Map<String, Object> payload =
                Map.of("answers", List.of(Map.of("questionId", ids.get(0), "scaleValue", 5)));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("질문 종류에 맞지 않는 답은 거부한다")
    void wrongAnswerShapeRejected() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = questionIds();
        // SCALE 질문에 텍스트를 보낸다.
        Map<String, Object> payload =
                Map.of("answers", List.of(Map.of("questionId", ids.get(0), "textValue", "5점")));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("보기에 없는 선택지는 거부한다")
    void unknownOptionRejected() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = questionIds();
        Map<String, Object> payload = Map.of(
                "answers",
                List.of(
                        Map.of("questionId", ids.get(0), "scaleValue", 5),
                        Map.of("questionId", ids.get(1), "selectedOptions", List.of("없는메뉴"))));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("척도 범위를 벗어난 값은 거부한다")
    void scaleOutOfRangeRejected() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = questionIds();
        Map<String, Object> payload = Map.of(
                "answers",
                List.of(
                        Map.of("questionId", ids.get(0), "scaleValue", 9),
                        Map.of("questionId", ids.get(1), "selectedOptions", List.of("제육덮밥"))));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("같은 질문에 두 번 답하면 거부한다")
    void duplicateQuestionRejected() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = questionIds();
        Map<String, Object> payload = Map.of(
                "answers",
                List.of(
                        Map.of("questionId", ids.get(0), "scaleValue", 5),
                        Map.of("questionId", ids.get(0), "scaleValue", 4),
                        Map.of("questionId", ids.get(1), "selectedOptions", List.of("제육덮밥"))));

        MvcResult result = postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses", payload, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("답이 실제로 저장된다")
    void answersArePersisted() throws Exception {
        JsonNode auth = signup();
        postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(auth));

        var stored = surveyResponseRepository.findAll().getFirst();
        assertThat(stored.getUserId()).isEqualTo(auth.path("user").path("id").asLong());
        assertThat(stored.getSurveyId()).isEqualTo(survey.getId());
    }

    @Test
    @DisplayName("없는 설문에 제출하면 404다")
    void unknownSurveyIsNotFound() throws Exception {
        JsonNode auth = signup();

        MvcResult result =
                postJson("/api/v1/surveys/999999/responses", validAnswers(), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("참여 여부 조회 API를 만들지 않았다")
    void noParticipationEndpoint() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/babplanner/backend/survey/controller/SurveyController.java"));

        assertThat(source).doesNotContain("participation");
        assertThat(source).doesNotContain("participated");
    }

    @Test
    @DisplayName("설문 제출이 포인트를 지급하지 않는다")
    void submitDoesNotGrantPoints() throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();

        postJson(
                "/api/v1/surveys/" + survey.getId() + "/responses",
                validAnswers(),
                accessTokenOf(auth));

        // 지급 규칙이 아직 확정되지 않았다. 서버가 임의로 만들지 않는다.
        assertThat(rewardAccountRepository.findByUserId(userId)).isEmpty();
        assertThat(rewardTransactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                .isEmpty();
    }
}

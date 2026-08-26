package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class AdminSurveyApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/surveys";

    @Autowired private SurveyRepository surveyRepository;

    /** 씨앗 설문이 섞이면 목록과 순서를 셀 수 없다. */
    @BeforeEach
    void clearSurveys() {
        surveyResponseRepository.deleteAll();
        surveyRepository.deleteAll();
        surveyRepository.flush();
    }

    private Map<String, Object> question(
            String text, String type, boolean required, List<String> options) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("type", type);
        body.put("required", required);
        body.put("options", options);
        return body;
    }

    private Map<String, Object> payload(Map<String, Object> overrides) {
        Instant now = clock.instant();
        Map<String, Object> body = new HashMap<>();
        body.put("title", "급식 만족도 조사");
        body.put("description", "이번 달 급식은 어떠셨나요?");
        body.put("startsAt", now.minus(Duration.ofHours(1)).toString());
        body.put("endsAt", now.plus(Duration.ofDays(7)).toString());
        body.put(
                "questions",
                List.of(question("전반적으로 만족하셨나요?", "SCALE", true, List.of())));
        body.putAll(overrides);
        return body;
    }

    private JsonNode created(String token, Map<String, Object> overrides) throws Exception {
        MvcResult result = postJson(PATH, payload(overrides), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data");
    }

    private void submit(String userToken, long surveyId, List<Map<String, Object>> answers)
            throws Exception {
        MvcResult result = postJson(
                "/api/v1/surveys/" + surveyId + "/responses",
                Map.of("answers", answers),
                userToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    private Map<String, Object> answer(long questionId, Map<String, Object> value) {
        Map<String, Object> body = new HashMap<>();
        body.put("questionId", questionId);
        body.putAll(value);
        return body;
    }

    private JsonNode summaryOf(JsonNode results, long questionId) {
        for (JsonNode result : results.path("questionResults")) {
            if (result.path("questionId").asLong() == questionId) {
                return result.path("summary");
            }
        }
        throw new AssertionError("문항 결과가 없습니다: " + questionId);
    }

    @Test
    @DisplayName("목록에 지난 설문과 예정된 설문이 모두 나온다")
    void listIncludesPastAndFuture() throws Exception {
        String token = adminToken();
        Instant now = clock.instant();
        created(token, Map.of(
                "title", "지난 설문",
                "startsAt", now.minus(Duration.ofDays(30)).toString(),
                "endsAt", now.minus(Duration.ofDays(20)).toString()));
        created(token, Map.of("title", "진행 중"));
        created(token, Map.of(
                "title", "예정",
                "startsAt", now.plus(Duration.ofDays(10)).toString(),
                "endsAt", now.plus(Duration.ofDays(20)).toString()));

        JsonNode data = body(getJson(PATH, token)).path("data");

        List<String> titles = new ArrayList<>();
        data.forEach(node -> titles.add(node.path("title").asText()));
        // 진행 중인 것만 주면 관리가 안 된다. 시작이 늦은 순.
        assertThat(titles).containsExactly("예정", "진행 중", "지난 설문");
    }

    @Test
    @DisplayName("등록은 201이고 문항을 담아 돌려준다")
    void createReturnsTheSurvey() throws Exception {
        JsonNode data = created(adminToken(), Map.of());

        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("title").asText()).isEqualTo("급식 만족도 조사");
        assertThat(data.path("questions")).hasSize(1);
    }

    @Test
    @DisplayName("문항 id는 서버가 만든다")
    void questionIdsAreGeneratedByTheServer() throws Exception {
        Map<String, Object> question = question("문항", "TEXT", false, List.of());
        question.put("id", 999999);

        JsonNode data = created(adminToken(), Map.of("questions", List.of(question)));

        // 클라이언트가 DB 키를 정하면 다른 설문의 문항을 덮어쓸 수 있다.
        assertThat(data.path("questions").get(0).path("id").asLong()).isNotEqualTo(999999L);
        assertThat(data.path("questions").get(0).path("id").asLong()).isPositive();
    }

    @Test
    @DisplayName("시작이 끝보다 늦으면 거부한다")
    void reversedPeriodIsRejected() throws Exception {
        Instant now = clock.instant();

        MvcResult result = postJson(
                PATH,
                payload(Map.of(
                        "startsAt", now.plus(Duration.ofDays(1)).toString(),
                        "endsAt", now.toString())),
                adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_SURVEY_PERIOD");
    }

    @Test
    @DisplayName("시작과 끝이 같으면 거부한다")
    void zeroLengthPeriodIsRejected() throws Exception {
        String moment = clock.instant().toString();

        MvcResult result = postJson(
                PATH, payload(Map.of("startsAt", moment, "endsAt", moment)), adminToken());

        // 열려 있는 순간이 없는 설문이다.
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("객관식은 선택지가 필요하다")
    void choiceQuestionNeedsOptions() throws Exception {
        MvcResult result = postJson(
                PATH,
                payload(Map.of(
                        "questions",
                        List.of(question("무엇이 좋았나요?", "SINGLE_CHOICE", true, List.of())))),
                adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_SURVEY_QUESTION");
    }

    @Test
    @DisplayName("주관식에는 선택지를 붙일 수 없다")
    void textQuestionRejectsOptions() throws Exception {
        MvcResult result = postJson(
                PATH,
                payload(Map.of(
                        "questions",
                        List.of(question("의견", "TEXT", false, List.of("가", "나"))))),
                adminToken());

        // 붙여 봐야 어디에도 쓰이지 않는다.
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("빈 선택지는 거부한다")
    void blankOptionIsRejected() throws Exception {
        MvcResult result = postJson(
                PATH,
                payload(Map.of(
                        "questions",
                        List.of(question("무엇이 좋았나요?", "SINGLE_CHOICE", true, List.of("맛", " "))))),
                adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("문항 네 종류를 모두 만든다")
    void allFourQuestionTypes() throws Exception {
        JsonNode data = created(adminToken(), Map.of("questions", List.of(
                question("하나만", "SINGLE_CHOICE", true, List.of("가", "나")),
                question("여러 개", "MULTIPLE_CHOICE", false, List.of("가", "나", "다")),
                question("점수", "SCALE", true, List.of()),
                question("의견", "TEXT", false, List.of()))));

        assertThat(data.path("questions")).hasSize(4);
    }

    @Test
    @DisplayName("문항이 없으면 400이다")
    void surveyWithoutQuestionsIsRejected() throws Exception {
        MvcResult result =
                postJson(PATH, payload(Map.of("questions", List.of())), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("응답 수는 사람 수다")
    void responseCountCountsPeople() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("점수", "SCALE", true, List.of()),
                question("의견", "TEXT", false, List.of()))));
        long surveyId = survey.path("id").asLong();
        long scaleId = survey.path("questions").get(0).path("id").asLong();
        long textId = survey.path("questions").get(1).path("id").asLong();

        String user = accessTokenOf(signup());
        submit(user, surveyId, List.of(
                answer(scaleId, Map.of("scaleValue", 4)),
                answer(textId, Map.of("textValue", "좋았어요"))));

        JsonNode results = body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data");

        // 한 사람이 두 문항에 답했다고 두 명으로 세면 안 된다.
        assertThat(results.path("responseCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("단일 선택 결과를 선택지별로 센다")
    void singleChoiceCounts() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("무엇이 좋았나요?", "SINGLE_CHOICE", true, List.of("맛", "양", "가격")))));
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();

        submit(accessTokenOf(signup()), surveyId,
                List.of(answer(questionId, Map.of("selectedOptions", List.of("맛")))));
        submit(accessTokenOf(signup("01098765432", "20205678")), surveyId,
                List.of(answer(questionId, Map.of("selectedOptions", List.of("맛")))));

        JsonNode summary = summaryOf(
                body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data"), questionId);

        assertThat(summary.path("optionCounts").path("맛").asInt()).isEqualTo(2);
        // 아무도 고르지 않은 선택지도 0으로 남긴다.
        assertThat(summary.path("optionCounts").path("양").asInt()).isZero();
        assertThat(summary.path("optionCounts").has("가격")).isTrue();
    }

    @Test
    @DisplayName("복수 선택은 고른 만큼 각각 센다")
    void multipleChoiceCountsEachSelection() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("무엇이 좋았나요?", "MULTIPLE_CHOICE", false, List.of("맛", "양", "가격")))));
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();

        submit(accessTokenOf(signup()), surveyId,
                List.of(answer(questionId, Map.of("selectedOptions", List.of("맛", "양")))));

        JsonNode summary = summaryOf(
                body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data"), questionId);

        assertThat(summary.path("optionCounts").path("맛").asInt()).isEqualTo(1);
        assertThat(summary.path("optionCounts").path("양").asInt()).isEqualTo(1);
        assertThat(summary.path("optionCounts").path("가격").asInt()).isZero();
        // 사람은 하나다.
        assertThat(summary.path("responseCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("척도는 평균과 분포를 낸다")
    void scaleAverageAndDistribution() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("점수", "SCALE", true, List.of()))));
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();

        submit(accessTokenOf(signup()), surveyId,
                List.of(answer(questionId, Map.of("scaleValue", 4))));
        submit(accessTokenOf(signup("01098765432", "20205678")), surveyId,
                List.of(answer(questionId, Map.of("scaleValue", 2))));

        JsonNode summary = summaryOf(
                body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data"), questionId);

        assertThat(summary.path("average").asDouble()).isEqualTo(3.0);
        assertThat(summary.path("responseCount").asInt()).isEqualTo(2);
        assertThat(summary.path("distribution").path("4").asInt()).isEqualTo(1);
        assertThat(summary.path("distribution").path("2").asInt()).isEqualTo(1);
        assertThat(summary.path("distribution").path("1").asInt()).isZero();
        // 라벨이 없으면 1~5로 본다.
        assertThat(summary.path("distribution").has("5")).isTrue();
    }

    @Test
    @DisplayName("답이 없는 척도는 평균이 없다")
    void scaleWithoutAnswersHasNoAverage() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of());
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();

        JsonNode summary = summaryOf(
                body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data"), questionId);

        // 0으로 두면 "전부 최하점"으로 읽힌다.
        assertThat(summary.path("average").isNull()).isTrue();
        assertThat(summary.path("responseCount").asInt()).isZero();
    }

    @Test
    @DisplayName("주관식은 답변을 모아 준다")
    void textResponsesAreCollected() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("의견", "TEXT", false, List.of()))));
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();

        submit(accessTokenOf(signup()), surveyId,
                List.of(answer(questionId, Map.of("textValue", "국이 짰어요"))));

        JsonNode summary = summaryOf(
                body(getJson(PATH + "/" + surveyId + "/results", admin)).path("data"), questionId);

        assertThat(summary.path("responseCount").asInt()).isEqualTo(1);
        assertThat(summary.path("responses").get(0).asText()).isEqualTo("국이 짰어요");
    }

    @Test
    @DisplayName("결과에 작성자 정보가 없다")
    void resultsCarryNoUserIdentity() throws Exception {
        String admin = adminToken();
        JsonNode survey = created(admin, Map.of("questions", List.of(
                question("의견", "TEXT", false, List.of()))));
        long surveyId = survey.path("id").asLong();
        long questionId = survey.path("questions").get(0).path("id").asLong();
        JsonNode user = signup();
        submit(accessTokenOf(user), surveyId,
                List.of(answer(questionId, Map.of("textValue", "의견입니다"))));

        String json = getJson(PATH + "/" + surveyId + "/results", admin)
                .getResponse()
                .getContentAsString();

        // 무엇이 얼마나 나왔는지는 알아야 하지만 누가 그랬는지는 아니다.
        assertThat(json)
                .doesNotContain("userId")
                .doesNotContain(PHONE)
                .doesNotContain(STUDENT_ID)
                .doesNotContain("\"name\"");
    }

    @Test
    @DisplayName("없는 설문 결과는 404다")
    void resultsOfUnknownSurveyAreNotFound() throws Exception {
        MvcResult result = getJson(PATH + "/999999/results", adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("SURVEY_NOT_FOUND");
    }

    @Test
    @DisplayName("사용자 진행 중 설문 조회가 그대로 동작한다")
    void activeSurveyEndpointStillWorks() throws Exception {
        String admin = adminToken();
        created(admin, Map.of("title", "진행 중"));

        JsonNode data = body(getJson("/api/v1/surveys/active", accessTokenOf(signup())))
                .path("data");

        assertThat(data.path("title").asText()).isEqualTo("진행 중");
    }
}

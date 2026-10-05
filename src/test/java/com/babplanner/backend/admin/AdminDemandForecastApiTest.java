package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.support.DemandForecastFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

/**
 * 관리자 식수 예측 API.
 *
 * <p>MockMvc → 보안 규칙 → 컨트롤러 → 서비스 → 운영 클라이언트까지 그대로 지난다.
 * 나가는 호출만 AI 서버를 흉내 낸 서버가 받는다. 이 클래스의 컨텍스트에서만
 * AI 클라이언트 공급자를 바꿔 끼운다.
 */
class AdminDemandForecastApiTest extends ApiIntegrationTest {

    private static final String PREDICT = "/api/v1/admin/demand-forecasts/predict";
    private static final String STATUS = "/api/v1/admin/demand-forecasts/status";
    /** test application.yml 의 bapplanner.ai.service-token. */
    private static final String AI_TOKEN = "test-only-ai-service-token-not-a-real-secret";
    private static final String AI_BASE = "http://ai.test.invalid";

    /** 운영 공급자와 같은 설정을 쓰되, 소켓 대신 흉내 낸 서버로 보낸다. */
    @TestConfiguration
    static class MockAiServerConfig {

        record MockAi(RestClient client, MockRestServiceServer server) {}

        @Bean
        MockAi mockAi() {
            RestClient.Builder builder = RestClient.builder().baseUrl(AI_BASE);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            return new MockAi(builder.build(), server);
        }

        @Bean
        @Primary
        AiRestClientProvider mockAiRestClientProvider(AiServiceProperties properties, MockAi mockAi) {
            return new AiRestClientProvider(properties) {
                @Override
                public RestClient client() {
                    return mockAi.client();
                }
            };
        }
    }

    @Autowired private MockAiServerConfig.MockAi mockAi;

    private MockRestServiceServer aiServer;

    @BeforeEach
    void resetAiServer() {
        aiServer = mockAi.server();
        aiServer.reset();
    }

    private Map<String, Object> payload() {
        Map<String, Object> body = new HashMap<>();
        body.put("date", "2026-09-15");
        body.put("semester", 2);
        body.put("semesterWeek", 3);
        body.put("examPeriod", false);
        body.put("holiday", false);
        body.put("campusEvent", false);
        body.put("temperature", 24.0);
        body.put("rainfall", 0.0);
        body.put("humidity", 60.0);
        body.put("menuCategory", "WESTERN");
        body.put("mainMenu", "돈까스");
        body.put("specialMenu", false);
        return body;
    }

    private Map<String, Object> payload(String field, Object value) {
        Map<String, Object> body = payload();
        body.put(field, value);
        return body;
    }

    private void aiPredictResponds(HttpStatus status, String body) {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/predict"))
                .andExpect(header("X-Service-Token", AI_TOKEN))
                .andRespond(status.is2xxSuccessful()
                        ? withSuccess(body, MediaType.APPLICATION_JSON)
                        : withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    // ---------- 정상 ----------

    @Test
    @DisplayName("관리자가 예측을 요청하면 FastAPI 결과를 감싸 돌려준다")
    void adminGetsTheForecast() throws Exception {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/predict"))
                .andExpect(header("X-Service-Token", AI_TOKEN))
                // 관리자는 enum 이름을 보내고, FastAPI는 한글 라벨을 받는다.
                .andExpect(jsonPath("$.menuCategory").value("양식"))
                .andExpect(jsonPath("$.date").value("2026-09-15"))
                .andRespond(withSuccess(DemandForecastFixtures.SUCCESS_BODY, MediaType.APPLICATION_JSON));

        MvcResult result = postJson(PREDICT, payload(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        JsonNode data = json.path("data");
        assertThat(data.path("forecastDate").asText()).isEqualTo("2026-09-15");
        assertThat(data.path("mealType").asText()).isEqualTo("LUNCH");
        assertThat(data.path("predictedMealCount").asInt()).isEqualTo(431);
        assertThat(data.path("reference").path("previousDay").asInt()).isEqualTo(350);
        assertThat(data.path("reference").path("sameWeekdayPreviousWeek").asInt()).isEqualTo(375);
        assertThat(data.path("reference").path("rolling7DayAverage").asInt()).isEqualTo(367);
        assertThat(data.path("reference").path("historyAsOf").asText()).isEqualTo("2026-09-11");
        assertThat(data.path("conditions").path("weekday").asText()).isEqualTo("화요일");
        assertThat(data.path("conditions").path("menuCategory").asText()).isEqualTo("WESTERN");
        assertThat(data.path("model").path("version").asText()).isEqualTo("54453980807e");
        assertThat(data.path("model").path("syntheticData").asBoolean()).isTrue();
        assertThat(data.path("disclaimer").asText()).contains("합성데이터 기반 PoC");
        assertThat(data.path("warnings")).hasSize(1);
        assertThat(data.path("predictedAt").asText()).isNotBlank();
        aiServer.verify();
    }

    @Test
    @DisplayName("응답이 AI 서버의 내부 경로를 드러내지 않는다")
    void responseDoesNotRevealTheAiServer() throws Exception {
        aiPredictResponds(HttpStatus.OK, DemandForecastFixtures.SUCCESS_BODY);

        String text = postJson(PREDICT, payload(), adminToken()).getResponse().getContentAsString();

        assertThat(text).doesNotContain("/internal/v1/ai").doesNotContain(AI_BASE).doesNotContain(AI_TOKEN);
    }

    // ---------- 검증 ----------

    private void assertRejected(String token, Map<String, Object> body) throws Exception {
        MvcResult result = postJson(PREDICT, body, token);

        assertThat(result.getResponse().getStatus()).as("%s", body).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        // 검증에 걸리면 AI 서버를 부르지 않는다. 불렀다면 기대가 없어 503이 났을 것이다.
    }

    @Test
    @DisplayName("범위를 벗어난 값은 400이다")
    void outOfRangeValuesAreRejected() throws Exception {
        String token = adminToken();

        assertRejected(token, payload("semester", 3));
        assertRejected(token, payload("semester", 0));
        assertRejected(token, payload("semesterWeek", 17));
        assertRejected(token, payload("humidity", 101));
        assertRejected(token, payload("humidity", -1));
        assertRejected(token, payload("rainfall", -0.5));
        assertRejected(token, payload("temperature", 60));
        assertRejected(token, payload("mainMenu", "  "));
    }

    @Test
    @DisplayName("정해진 메뉴 분류만 받는다")
    void unknownMenuCategoryIsRejected() throws Exception {
        String token = adminToken();

        assertRejected(token, payload("menuCategory", "FUSION"));
        // 관리자 API는 다른 메뉴 API처럼 enum 이름을 쓴다. 한글 라벨은 AI 서버 계약이다.
        assertRejected(token, payload("menuCategory", "양식"));
    }

    @Test
    @DisplayName("주말은 400이다")
    void weekendIsRejected() throws Exception {
        MvcResult result = postJson(PREDICT, payload("date", "2026-09-12"), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("details").path("fields").has("dateOnWeekday"))
                .isTrue();
    }

    @Test
    @DisplayName("필수 값이 빠지면 400이다")
    void missingFieldsAreRejected() throws Exception {
        String token = adminToken();

        for (String field : payload().keySet()) {
            Map<String, Object> body = payload();
            body.remove(field);

            assertRejected(token, body);
        }
    }

    // ---------- AI 서버를 쓸 수 없을 때 ----------

    private void assertUnavailable(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("error").path("code").asText()).isEqualTo("AI_SERVICE_UNAVAILABLE");
        // 대신할 숫자를 만들지 않는다.
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain("predictedMealCount");
        // 내부 주소, 토큰, 스택 트레이스, AI 서버의 내부 사정을 흘리지 않는다.
        String text = result.getResponse().getContentAsString();
        assertThat(text)
                .doesNotContain(AI_BASE)
                .doesNotContain("/internal/v1/ai")
                .doesNotContain(AI_TOKEN)
                .doesNotContain("Exception")
                .doesNotContain("model artifact");
    }

    @Test
    @DisplayName("FastAPI가 503(모델 없음)이면 503이고 숫자가 없다")
    void aiServiceUnavailableIs503() throws Exception {
        aiPredictResponds(
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"detail\":\"식수 예측을 사용할 수 없습니다: model artifact not found\"}");

        assertUnavailable(postJson(PREDICT, payload(), adminToken()));
    }

    @Test
    @DisplayName("FastAPI가 401(토큰 불일치)이면 503이다")
    void aiUnauthorizedIs503() throws Exception {
        aiPredictResponds(HttpStatus.UNAUTHORIZED, "{\"detail\":\"서비스 토큰이 올바르지 않습니다.\"}");

        assertUnavailable(postJson(PREDICT, payload(), adminToken()));
    }

    @Test
    @DisplayName("FastAPI 응답이 망가졌으면 503이다")
    void malformedAiResponseIs503() throws Exception {
        aiPredictResponds(HttpStatus.OK, "<html>not json</html>");

        assertUnavailable(postJson(PREDICT, payload(), adminToken()));
    }

    @Test
    @DisplayName("FastAPI에 닿지 못하면 503이다")
    void unreachableAiIs503() throws Exception {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/predict"))
                .andRespond(withException(new IOException("Connection refused")));

        assertUnavailable(postJson(PREDICT, payload(), adminToken()));
    }

    // ---------- status ----------

    @Test
    @DisplayName("status가 모델 상태를 전한다")
    void statusIsRelayed() throws Exception {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/status"))
                .andExpect(header("X-Service-Token", AI_TOKEN))
                .andRespond(withSuccess("""
                        {"available": true, "modelLoaded": true, "historyAvailable": true,
                         "modelName": "lightgbm", "modelVersion": "54453980807e",
                         "target": "meal_count", "syntheticData": true,
                         "trainingPeriod": ["2023-03-13", "2026-09-11"]}
                        """, MediaType.APPLICATION_JSON));

        MvcResult result = getJson(STATUS, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("available").asBoolean()).isTrue();
        assertThat(data.path("modelLoaded").asBoolean()).isTrue();
        assertThat(data.path("syntheticData").asBoolean()).isTrue();
        assertThat(data.path("modelVersion").asText()).isEqualTo("54453980807e");
    }

    @Test
    @DisplayName("모델이 없어도 status는 200이고 이유를 전한다")
    void statusWithoutModelIs200() throws Exception {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/status"))
                .andRespond(withSuccess("""
                        {"available": false, "modelLoaded": false, "historyAvailable": true,
                         "reason": "model artifact not found"}
                        """, MediaType.APPLICATION_JSON));

        MvcResult result = getJson(STATUS, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("available").asBoolean()).isFalse();
        assertThat(data.path("modelLoaded").asBoolean()).isFalse();
        assertThat(data.path("reason").asText()).isEqualTo("model artifact not found");
    }

    @Test
    @DisplayName("AI 서버에 닿지 못하면 status는 503이다")
    void unreachableStatusIs503() throws Exception {
        aiServer.expect(requestTo(AI_BASE + "/internal/v1/ai/demand-forecast/status"))
                .andRespond(withException(new IOException("Connection refused")));

        MvcResult result = getJson(STATUS, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(postJson(PREDICT, payload()).getResponse().getStatus()).isEqualTo(401);
        assertThat(getJson(STATUS).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이고 AI 서버를 부르지 않는다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(postJson(PREDICT, payload(), token).getResponse().getStatus()).isEqualTo(403);
        assertThat(getJson(STATUS, token).getResponse().getStatus()).isEqualTo(403);
        // 기대를 등록하지 않았다. 불렸다면 verify 전에 요청 자체가 실패했을 것이다.
        aiServer.verify();
    }
}

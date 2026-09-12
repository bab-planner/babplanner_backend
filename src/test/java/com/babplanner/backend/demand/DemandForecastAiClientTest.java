package com.babplanner.backend.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.demand.client.DemandForecastAiClient;
import com.babplanner.backend.demand.client.DemandForecastAiRequest;
import com.babplanner.backend.demand.client.DemandForecastAiResponse;
import com.babplanner.backend.demand.client.DemandForecastAiStatus;
import com.babplanner.backend.support.DemandForecastFixtures;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * FastAPI 식수 예측 클라이언트.
 *
 * <p>AI 서버를 흉내 낸 서버에 붙인다. 계약 확인 코드는 운영과 같은 것을 지난다.
 */
class DemandForecastAiClientTest {

    static final String BASE_URL = "http://ai.test";
    static final String TOKEN = "test-only-ai-service-token-not-a-real-secret";
    static final String PREDICT_URL = BASE_URL + "/internal/v1/ai/demand-forecast/predict";
    static final String STATUS_URL = BASE_URL + "/internal/v1/ai/demand-forecast/status";

    static final String SUCCESS_BODY = DemandForecastFixtures.SUCCESS_BODY;

    private MockRestServiceServer aiServer;
    private DemandForecastAiClient client;

    static DemandForecastAiRequest request() {
        return new DemandForecastAiRequest(
                "2026-09-15", 2, 3, false, false, false, 24.0, 0.0, 60.0, "양식", "돈까스", 0.9,
                false);
    }

    /** 흉내 낸 서버에 붙은 RestClient를 쓰도록 provider만 바꿔 끼운다. */
    static DemandForecastAiClient clientWith(RestClient restClient) {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceToken(TOKEN);
        return new DemandForecastAiClient(new AiRestClientProvider(properties) {
            @Override
            public RestClient client() {
                return restClient;
            }
        });
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        aiServer = MockRestServiceServer.bindTo(builder).build();
        client = clientWith(builder.build());
    }

    private void predictResponds(HttpStatus status, String body) {
        aiServer.expect(requestTo(PREDICT_URL))
                .andRespond(status.is2xxSuccessful()
                        ? withSuccess(body, MediaType.APPLICATION_JSON)
                        : withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    // ---------- 정상 ----------

    @Test
    @DisplayName("정상 응답을 빠짐없이 옮긴다")
    void mapsTheSuccessfulResponse() {
        predictResponds(HttpStatus.OK, SUCCESS_BODY);

        DemandForecastAiResponse response = client.predict(request());

        assertThat(response.forecastDate()).isEqualTo("2026-09-15");
        assertThat(response.mealType()).isEqualTo("LUNCH");
        assertThat(response.predictedMealCount()).isEqualTo(431);
        assertThat(response.reference().previousDay()).isEqualTo(350);
        assertThat(response.reference().sameWeekdayPreviousWeek()).isEqualTo(375);
        assertThat(response.reference().rolling7DayAverage()).isEqualTo(367);
        assertThat(response.reference().historyAsOf()).isEqualTo("2026-09-11");
        assertThat(response.conditions().weekday()).isEqualTo("화요일");
        assertThat(response.conditions().menuCategory()).isEqualTo("양식");
        assertThat(response.model().name()).isEqualTo("lightgbm");
        assertThat(response.model().version()).isEqualTo("54453980807e");
        assertThat(response.model().syntheticData()).isTrue();
        assertThat(response.model().trainedAt()).isEqualTo("2026-09-12T23:43:37");
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.disclaimer()).contains("합성데이터 기반 PoC");
    }

    @Test
    @DisplayName("서비스 토큰을 헤더에 싣고 FastAPI 계약대로 보낸다")
    void sendsTheTokenAndTheContract() {
        aiServer.expect(requestTo(PREDICT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", TOKEN))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.date").value("2026-09-15"))
                .andExpect(jsonPath("$.semester").value(2))
                .andExpect(jsonPath("$.semesterWeek").value(3))
                .andExpect(jsonPath("$.examPeriod").value(false))
                .andExpect(jsonPath("$.holiday").value(false))
                .andExpect(jsonPath("$.campusEvent").value(false))
                .andExpect(jsonPath("$.temperature").value(24.0))
                .andExpect(jsonPath("$.rainfall").value(0.0))
                .andExpect(jsonPath("$.humidity").value(60.0))
                // FastAPI는 한글 라벨을 받는다.
                .andExpect(jsonPath("$.menuCategory").value("양식"))
                .andExpect(jsonPath("$.mainMenu").value("돈까스"))
                .andExpect(jsonPath("$.menuPopularity").value(0.9))
                .andExpect(jsonPath("$.specialMenu").value(false))
                // 과거 feature와 사용자 정보는 보내지 않는다.
                .andExpect(jsonPath("$.lag_1").doesNotExist())
                .andExpect(jsonPath("$.weekday").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.predict(request());

        aiServer.verify();
    }

    @Test
    @DisplayName("AI 서버가 필드를 늘려도 읽는다")
    void unknownFieldsAreTolerated() {
        predictResponds(HttpStatus.OK, SUCCESS_BODY.replace(
                "\"mealType\": \"LUNCH\",", "\"mealType\": \"LUNCH\", \"newField\": {\"x\": 1},"));

        assertThat(client.predict(request()).predictedMealCount()).isEqualTo(431);
    }

    @Test
    @DisplayName("warnings가 없으면 빈 목록이다")
    void missingWarningsBecomeEmpty() {
        predictResponds(HttpStatus.OK, SUCCESS_BODY.replaceAll("\"warnings\": \\[[^]]*],", ""));

        assertThat(client.predict(request()).warnings()).isEmpty();
    }

    @Test
    @DisplayName("status를 부르고 옮긴다")
    void readsTheStatus() {
        aiServer.expect(requestTo(STATUS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"available": true, "modelLoaded": true, "historyAvailable": true,
                         "modelName": "lightgbm", "modelVersion": "54453980807e",
                         "target": "meal_count", "syntheticData": true,
                         "trainingPeriod": ["2023-03-13", "2026-09-11"]}
                        """, MediaType.APPLICATION_JSON));

        DemandForecastAiStatus status = client.status();

        assertThat(status.available()).isTrue();
        assertThat(status.modelLoaded()).isTrue();
        assertThat(status.syntheticData()).isTrue();
        assertThat(status.modelVersion()).isEqualTo("54453980807e");
        assertThat(status.trainingPeriod()).containsExactly("2023-03-13", "2026-09-11");
    }

    @Test
    @DisplayName("모델이 없다는 status는 예외가 아니라 정상 응답이다")
    void unavailableStatusIsAnAnswer() {
        aiServer.expect(requestTo(STATUS_URL)).andRespond(withSuccess("""
                {"available": false, "modelLoaded": false, "historyAvailable": true,
                 "reason": "model artifact not found"}
                """, MediaType.APPLICATION_JSON));

        DemandForecastAiStatus status = client.status();

        assertThat(status.available()).isFalse();
        assertThat(status.reason()).isEqualTo("model artifact not found");
    }

    // ---------- 실패 ----------

    private void assertFails(HttpStatus status, String body) {
        predictResponds(status, body);

        assertThatThrownBy(() -> client.predict(request())).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("FastAPI 503(artifact 없음)이면 실패한다")
    void aiUnavailableFails() {
        assertFails(
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"detail\":\"식수 예측을 사용할 수 없습니다: model artifact not found\"}");
    }

    @Test
    @DisplayName("FastAPI 401(토큰 불일치)이면 실패한다")
    void aiUnauthorizedFails() {
        assertFails(HttpStatus.UNAUTHORIZED, "{\"detail\":\"서비스 토큰이 올바르지 않습니다.\"}");
    }

    @Test
    @DisplayName("FastAPI 422나 500이어도 실패한다")
    void otherErrorsFail() {
        assertFails(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":[]}");
        setUp();
        assertFails(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    @Test
    @DisplayName("연결이 끊기면 실패한다")
    void ioFailureFails() {
        aiServer.expect(requestTo(PREDICT_URL)).andRespond(withException(new IOException("reset")));

        assertThatThrownBy(() -> client.predict(request())).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("망가진 응답이면 실패한다")
    void malformedResponseFails() {
        assertFails(HttpStatus.OK, "not json at all");
    }

    @Test
    @DisplayName("빈 응답이면 실패한다")
    void emptyResponseFails() {
        assertFails(HttpStatus.OK, "");
    }

    @Test
    @DisplayName("다른 날짜의 결과를 받지 않는다")
    void dateMismatchFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace(
                "\"forecastDate\": \"2026-09-15\"", "\"forecastDate\": \"2026-09-16\""));
    }

    @Test
    @DisplayName("중식이 아닌 결과를 받지 않는다")
    void nonLunchFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace("\"LUNCH\"", "\"DINNER\""));
    }

    @Test
    @DisplayName("예측 식수가 없거나 음수면 받지 않는다")
    void missingOrNegativeCountFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace("\"predictedMealCount\": 431,", ""));
        setUp();
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace("431", "-1"));
    }

    @Test
    @DisplayName("모델 버전이 없으면 받지 않는다")
    void missingModelVersionFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace("\"54453980807e\"", "\"  \""));
    }

    @Test
    @DisplayName("합성데이터 여부가 없으면 받지 않는다")
    void missingSyntheticFlagFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replace("\"syntheticData\": true,", ""));
    }

    @Test
    @DisplayName("disclaimer가 없으면 받지 않는다")
    void missingDisclaimerFails() {
        assertFails(HttpStatus.OK, SUCCESS_BODY.replaceAll(",\\s*\"disclaimer\": \"[^\"]*\"", ""));
    }

    @Test
    @DisplayName("status에 필수 필드가 없으면 실패한다")
    void statusWithoutRequiredFieldsFails() {
        aiServer.expect(requestTo(STATUS_URL))
                .andRespond(withSuccess("{\"reason\": \"?\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.status()).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("status 401도 실패다")
    void statusUnauthorizedFails() {
        aiServer.expect(requestTo(STATUS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.status()).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("실패해도 다시 보내지 않는다")
    void failureIsNotRetried() {
        // 기대를 한 번만 등록한다. 두 번 부르면 verify에서 실패한다.
        predictResponds(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        assertThatThrownBy(() -> client.predict(request())).isInstanceOf(AiServiceException.class);
        aiServer.verify();
    }

    @Test
    @DisplayName("AI 주소나 토큰이 설정되지 않았으면 실패한다")
    void missingConfigurationFails() {
        DemandForecastAiClient unconfigured =
                new DemandForecastAiClient(new AiRestClientProvider(new AiServiceProperties()));

        assertThatThrownBy(() -> unconfigured.predict(request()))
                .isInstanceOf(AiServiceException.class);
        assertThatThrownBy(unconfigured::status).isInstanceOf(AiServiceException.class);
    }
}

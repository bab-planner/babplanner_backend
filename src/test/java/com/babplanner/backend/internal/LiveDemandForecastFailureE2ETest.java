package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FastAPI 식수 예측을 쓸 수 없을 때의 종단 확인.
 *
 * <p>{@code AI_FAILURE_BASE_URL} 과 {@code AI_FAILURE_TOKEN} 을 주고 돌린다. 죽은 주소를
 * 주면 연결 실패를, 살아 있는 주소에 틀린 토큰을 주면 FastAPI 401을 재현한다. 둘 다
 * 관리자에게는 숫자 없는 503이어야 한다.
 */
@EnabledIfEnvironmentVariable(named = "AI_FAILURE_BASE_URL", matches = ".+")
class LiveDemandForecastFailureE2ETest extends ApiIntegrationTest {

    @DynamicPropertySource
    static void brokenAiService(DynamicPropertyRegistry registry) {
        registry.add("bapplanner.ai.base-url", () -> System.getenv("AI_FAILURE_BASE_URL"));
        registry.add("bapplanner.ai.service-token", () -> System.getenv("AI_FAILURE_TOKEN"));
        registry.add("bapplanner.ai.connect-timeout", () -> "2s");
        registry.add("bapplanner.ai.read-timeout", () -> "5s");
    }

    private static Map<String, Object> payload() {
        Map<String, Object> body = new LinkedHashMap<>();
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
        body.put("menuPopularity", 0.9);
        body.put("specialMenu", false);
        return body;
    }

    @Test
    @DisplayName("예측할 수 없으면 숫자 없는 503이다")
    void predictionIsUnavailable() throws Exception {
        MvcResult result = postJson(
                "/api/v1/admin/demand-forecasts/predict", payload(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        JsonNode json = body(result);
        assertThat(json.path("error").path("code").asText()).isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(System.getenv("AI_FAILURE_BASE_URL"))
                .doesNotContain("/internal/v1/ai");
    }

    @Test
    @DisplayName("status도 503이다")
    void statusIsUnavailable() throws Exception {
        MvcResult result = getJson("/api/v1/admin/demand-forecasts/status", adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
    }
}

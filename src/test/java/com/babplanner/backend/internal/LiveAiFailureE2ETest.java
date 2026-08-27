package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.procurement.repository.ProcurementRunRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI 서비스가 정상이 아닐 때의 종단 확인.
 *
 * <p>{@code AI_FAILURE_BASE_URL} 과 {@code AI_FAILURE_TOKEN} 을 주고 돌린다.
 * 죽은 주소를 주면 연결 실패를, 살아 있는 주소에 틀린 토큰을 주면 AI 401을
 * 재현한다. 둘 다 관리자에게는 같은 상황이어야 한다.
 *
 * <p>확인하려는 것은 상태 코드만이 아니다. <b>아무것도 저장되지 않았는가</b>가
 * 더 중요하다. 실패한 실행이 행으로 남으면 나중에 목록에서 그게 무엇인지 알
 * 수 없고, 반쯤 저장된 발주 목록은 그걸 보고 장을 보는 사람을 헛걸음시킨다.
 */
@EnabledIfEnvironmentVariable(named = "AI_FAILURE_BASE_URL", matches = ".+")
class LiveAiFailureE2ETest extends ApiIntegrationTest {

    private static final String DEMAND_PATH = "/api/v1/admin/demand-predictions";
    private static final String PROCUREMENT_PATH = "/api/v1/admin/procurement-recommendations";
    private static final LocalDate DAY = LocalDate.parse("2026-09-15");

    @Autowired private DemandPredictionRepository predictionRepository;
    @Autowired private ProcurementRunRepository runRepository;
    @Autowired private DiningSiteRepository siteRepository;

    @DynamicPropertySource
    static void brokenAiService(DynamicPropertyRegistry registry) {
        registry.add("bapplanner.ai.base-url", () -> System.getenv("AI_FAILURE_BASE_URL"));
        registry.add("bapplanner.ai.service-token", () -> System.getenv("AI_FAILURE_TOKEN"));
        // 죽은 주소를 기다리느라 오래 붙잡히지 않게 한다.
        registry.add("bapplanner.ai.connect-timeout", () -> "2s");
        registry.add("bapplanner.ai.read-timeout", () -> "5s");
    }

    @BeforeEach
    @AfterEach
    void clearRuns() {
        runRepository.deleteAll();
        runRepository.flush();
        predictionRepository.deleteAll();
        predictionRepository.flush();
    }

    private long siteId() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst().getId();
    }

    private Map<String, Object> demandBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("siteId", siteId());
        body.put("serviceDate", DAY.toString());
        body.put("mealType", "LUNCH");
        return body;
    }

    private Map<String, Object> procurementBody() {
        Map<String, Object> body = demandBody();
        body.put(
                "plannedIngredients",
                List.of(Map.of("ingredientName", "쌀", "unit", "kg", "plannedQuantity", 40.0)));
        return body;
    }

    /** 발주는 예측이 있어야 시작된다. AI를 거치지 않고 직접 심는다. */
    private DemandPrediction seedPrediction() {
        return predictionRepository.save(new DemandPrediction(
                siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst(),
                DAY,
                MealType.LUNCH,
                120,
                "fastapi-fixture-demand-v1",
                "demand-seeded-for-failure-check",
                clock.instant()));
    }

    @Test
    @DisplayName("예측 실행이 503으로 끝나고 행이 남지 않는다")
    void demandFailsWithoutStoringAnything() throws Exception {
        MvcResult result = postJson(DEMAND_PATH, demandBody(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(predictionRepository.count()).isZero();
    }

    @Test
    @DisplayName("발주 실행이 503으로 끝나고 실행도 항목도 남지 않는다")
    void procurementFailsWithoutStoringAnything() throws Exception {
        seedPrediction();

        MvcResult result = postJson(PROCUREMENT_PATH, procurementBody(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("실패 응답이 원인이나 비밀 값을 흘리지 않는다")
    void failureResponseLeaksNothing() throws Exception {
        String responseBody =
                postJson(DEMAND_PATH, demandBody(), adminToken()).getResponse().getContentAsString();

        // 관리자 화면에 스택트레이스나 토큰이 보이면 안 된다.
        for (String forbidden : List.of(
                "Exception",
                "RestClient",
                "Connection refused",
                "X-Service-Token",
                System.getenv("AI_FAILURE_TOKEN"))) {
            if (forbidden != null && !forbidden.isBlank()) {
                assertThat(responseBody).as(forbidden).doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("이미 저장된 데이터는 조회로 계속 보인다")
    void storedDataStaysReadable() throws Exception {
        DemandPrediction seeded = seedPrediction();
        String token = adminToken();

        // 실행이 실패해도 지난 결과를 못 보게 되면 안 된다.
        postJson(DEMAND_PATH, demandBody(), token);

        JsonNode listed = body(getJson(
                        DEMAND_PATH + "?siteId=" + siteId() + "&date=" + DAY, token))
                .path("data");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("id").asLong()).isEqualTo(seeded.getId());
    }
}

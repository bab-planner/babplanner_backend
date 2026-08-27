package com.babplanner.backend.procurement.client;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.security.ServiceTokenAuthenticationFilter;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.procurement.client.ProcurementRecommendationResult.RecommendedIngredient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * 별도 프로세스로 떠 있는 AI 서비스에 발주 추천을 요청한다.
 *
 * <p>보내는 것은 무엇을 언제 몇 인분 만들 계획이고 무엇을 얼마나 살
 * 계획인지뿐이다. 관리자가 누구인지는 추천에 필요하지 않다.
 *
 * <p>과거 잔반 집계를 보내지 않는다. 어느 기간의 무엇을 집계한 값인지 정의된
 * 적이 없다.
 *
 * <p>응답 검증이 이 클래스의 핵심이다. 재료가 하나라도 빠지거나 늘거나 겹치면
 * 저장하지 않는다. 빠진 채로 저장되면 그 목록을 보고 발주하는 사람은 그
 * 재료를 사지 않고, 늘어난 채로 저장되면 사지 않아도 될 것을 산다.
 */
@Component
public class HttpProcurementRecommender implements ProcurementRecommender {

    private static final Logger log = LoggerFactory.getLogger(HttpProcurementRecommender.class);

    static final String RECOMMEND_PATH = "/internal/v1/ai/procurement/recommend";

    private final AiRestClientProvider clients;

    public HttpProcurementRecommender(AiRestClientProvider clients) {
        this.clients = clients;
    }

    @Override
    public ProcurementRecommendationResult recommend(
            String requestId,
            long siteId,
            LocalDate serviceDate,
            MealType mealType,
            int predictedPeople,
            List<PlannedIngredient> planned) {
        AiProcurementResponse response;
        try {
            response = clients.client()
                    .post()
                    .uri(RECOMMEND_PATH)
                    .header(
                            ServiceTokenAuthenticationFilter.SERVICE_TOKEN_HEADER,
                            clients.serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(
                            requestId, siteId, serviceDate, mealType, predictedPeople, planned))
                    .retrieve()
                    .body(AiProcurementResponse.class);
        } catch (RestClientException exception) {
            // 상태 코드는 남기되 본문이나 헤더는 남기지 않는다.
            log.warn("발주 추천 요청이 실패했습니다. requestId={}", requestId);
            throw new AiServiceException("추천 서비스를 호출하지 못했습니다.", exception);
        }

        return validated(requestId, planned, response);
    }

    private Map<String, Object> requestBody(
            String requestId,
            long siteId,
            LocalDate serviceDate,
            MealType mealType,
            int predictedPeople,
            List<PlannedIngredient> planned) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("siteId", siteId);
        body.put("serviceDate", serviceDate.toString());
        body.put("mealType", mealType.name());
        body.put("predictedPeople", predictedPeople);
        body.put(
                "plannedIngredients",
                planned.stream()
                        .map(item -> Map.<String, Object>of(
                                "ingredientName", item.ingredientName(),
                                "unit", item.unit(),
                                "plannedQuantity", item.plannedQuantity()))
                        .toList());
        return body;
    }

    /** 계약을 벗어난 응답은 결과로 받아들이지 않는다. */
    private ProcurementRecommendationResult validated(
            String requestId, List<PlannedIngredient> planned, AiProcurementResponse response) {
        if (response == null) {
            throw new AiServiceException("추천 서비스가 빈 응답을 돌려줬습니다.");
        }
        if (!requestId.equals(response.requestId())) {
            throw new AiServiceException("추천 응답의 요청 식별자가 일치하지 않습니다.");
        }
        if (response.modelVersion() == null || response.modelVersion().isBlank()) {
            throw new AiServiceException("추천 응답에 모델 버전이 없습니다.");
        }
        List<AiRecommendation> items = response.recommendations();
        if (items == null || items.size() != planned.size()) {
            throw new AiServiceException("추천 항목 수가 요청과 다릅니다.");
        }

        Map<String, PlannedIngredient> byKey = new HashMap<>();
        for (PlannedIngredient item : planned) {
            byKey.put(item.matchKey(), item);
        }

        Set<String> seen = new HashSet<>();
        List<RecommendedIngredient> matched = new ArrayList<>();
        for (AiRecommendation item : items) {
            matched.add(match(item, byKey, seen));
        }

        // 개수가 같고 중복이 없으며 전부 입력에 있었으면 빠진 것도 없다.
        return new ProcurementRecommendationResult(List.copyOf(matched), response.modelVersion());
    }

    private RecommendedIngredient match(
            AiRecommendation item, Map<String, PlannedIngredient> byKey, Set<String> seen) {
        if (item == null || item.ingredientName() == null || item.unit() == null) {
            throw new AiServiceException("추천 항목에 재료 이름이나 단위가 없습니다.");
        }

        PlannedIngredient source = byKey.get(
                new PlannedIngredient(item.ingredientName(), item.unit(), 0).matchKey());
        if (source == null) {
            // 보내지 않은 재료가 돌아왔다. 그대로 저장하면 사지 않아도 될
            // 것을 사게 된다.
            throw new AiServiceException("요청하지 않은 재료가 추천에 포함돼 있습니다.");
        }
        if (!seen.add(source.matchKey())) {
            // 같은 재료가 두 줄이면 어느 쪽 수량으로 발주할지 알 수 없다.
            throw new AiServiceException("같은 재료가 추천에 두 번 들어 있습니다.");
        }
        if (item.plannedQuantity() != null
                && Double.compare(item.plannedQuantity(), source.plannedQuantity()) != 0) {
            // 계획 수량은 관리자가 적은 값이다. 응답이 바꿔서 돌려주면
            // 무엇을 기준으로 조정한 것인지 알 수 없다.
            throw new AiServiceException("추천 항목의 계획 수량이 요청과 다릅니다.");
        }
        requireFinite(item.recommendedQuantity(), "추천 수량");
        if (item.recommendedQuantity() < 0) {
            throw new AiServiceException("추천 수량이 음수입니다.");
        }
        if (item.adjustmentRate() != null) {
            requireFinite(item.adjustmentRate(), "조정률");
        }

        return new RecommendedIngredient(
                // 저장되는 이름과 단위는 관리자가 적은 값이다. 응답이 대소문자를
                // 바꿔 보내도 화면에는 적은 대로 나와야 한다.
                source.ingredientName(),
                source.unit(),
                source.plannedQuantity(),
                item.recommendedQuantity(),
                item.adjustmentRate(),
                item.reason());
    }

    private void requireFinite(Double value, String name) {
        if (value == null) {
            throw new AiServiceException(name + "가 없습니다.");
        }
        if (value.isNaN() || value.isInfinite()) {
            // NaN이 수량으로 저장되면 비교도 합계도 조용히 무너진다.
            throw new AiServiceException(name + "가 숫자가 아닙니다.");
        }
    }

    /** OpenAPI AIProcurementResponse. */
    record AiProcurementResponse(
            String requestId, List<AiRecommendation> recommendations, String modelVersion) {}

    /** OpenAPI ProcurementItem. */
    record AiRecommendation(
            String ingredientName,
            String unit,
            Double plannedQuantity,
            Double recommendedQuantity,
            Double adjustmentRate,
            String reason) {}
}

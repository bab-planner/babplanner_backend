package com.babplanner.backend.procurement.client;

import com.babplanner.backend.menu.domain.MealType;
import java.time.LocalDate;
import java.util.List;

/**
 * 발주 추천 경계.
 *
 * <p>백엔드 안에 "그럴듯한 수량"을 내는 구현을 만들지 않는다. 그런 구현이
 * 있으면 AI 서비스가 꺼져 있어도 화면에는 추천이 뜨고, 관리자는 그 수량으로
 * 실제 발주를 넣는다.
 */
public interface ProcurementRecommender {

    /**
     * @param requestId 한 번의 실행을 가리키는 상관 ID. 사용자 식별자가 아니다
     * @param planned 관리자가 명시한 계획 수량. 응답은 이 목록과 정확히
     *     대응해야 한다
     * @throws com.babplanner.backend.common.client.AiServiceException 호출하지
     *     못했거나 응답이 계약을 벗어난 경우
     */
    ProcurementRecommendationResult recommend(
            String requestId,
            long siteId,
            LocalDate serviceDate,
            MealType mealType,
            int predictedPeople,
            List<PlannedIngredient> planned);
}

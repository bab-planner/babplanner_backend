package com.babplanner.backend.demand.client;

import com.babplanner.backend.menu.domain.MealType;
import java.time.LocalDate;

/**
 * 수요 예측 경계.
 *
 * <p>구현은 별도 프로세스를 호출한다. 서비스 코드가 예측 숫자를 직접 만들지
 * 않게 하려고 인터페이스를 둔다. 백엔드 안에 "그럴듯한 값"을 내는 구현을
 * 만들면, AI 서비스가 꺼져 있어도 화면에는 예측이 뜬다.
 */
public interface DemandPredictor {

    /**
     * @param requestId 한 번의 실행을 가리키는 상관 ID. 사용자 식별자가 아니다
     * @throws com.babplanner.backend.common.client.AiServiceException 호출하지
     *     못했거나 응답이 계약을 벗어난 경우
     */
    DemandPredictionResult predict(
            String requestId, long siteId, LocalDate serviceDate, MealType mealType);
}

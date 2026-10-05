package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.DemandForecastRequest;
import com.babplanner.backend.admin.dto.DemandForecastResponse;
import com.babplanner.backend.admin.dto.DemandForecastStatusResponse;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.demand.client.DemandForecastAiClient;
import com.babplanner.backend.demand.client.DemandForecastAiRequest;
import com.babplanner.backend.demand.client.DemandForecastAiResponse;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 관리자 식수 예측 (합성데이터 PoC).
 *
 * <p>Controller → 이 서비스 → {@link DemandForecastAiClient} → FastAPI. 백엔드는 모델을
 * 모른다. 무엇을 보냈는지, 몇 명이 나왔는지, 어느 모델이 냈는지, 합성데이터 모델인지,
 * 경고가 있는지만 다룬다.
 *
 * <p><b>예측할 수 없으면 예측 불가다.</b> AI 서버가 꺼졌거나, 시간이 초과됐거나,
 * 503/401을 돌려줬거나, 응답이 계약을 벗어나면 {@code AI_SERVICE_UNAVAILABLE}(503)로
 * 끝낸다. 대신할 숫자를 만들지 않는다. 그 숫자로 식재료를 주문하게 된다.
 *
 * <p>지금은 결과를 저장하지 않는다. 저장은 기존 수요 예측 실행
 * ({@link AdminDemandService})이 하고, 이 PoC 예측은 조회용이다.
 */
@Service
public class AdminDemandForecastService {

    private final DemandForecastAiClient client;
    private final Clock clock;

    public AdminDemandForecastService(DemandForecastAiClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public DemandForecastResponse predict(DemandForecastRequest request) {
        DemandForecastAiResponse response;
        try {
            response = client.predict(toAiRequest(request));
        } catch (AiServiceException exception) {
            // 원인은 클라이언트가 서버 로그에 남겼다. 관리자에게는 "지금은 예측할 수
            // 없다"만 알린다. 내부 주소, 토큰, 스택 트레이스는 응답에 싣지 않는다.
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        return DemandForecastResponse.of(request, response, clock.instant());
    }

    public DemandForecastStatusResponse status() {
        try {
            return DemandForecastStatusResponse.from(client.status());
        } catch (AiServiceException exception) {
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /** 관리자 요청을 AI 서버 계약으로 옮긴다. 메뉴 분류만 한글 라벨로 바뀐다. */
    static DemandForecastAiRequest toAiRequest(DemandForecastRequest request) {
        return new DemandForecastAiRequest(
                request.date().toString(),
                request.semester(),
                request.semesterWeek(),
                request.examPeriod(),
                request.holiday(),
                request.campusEvent(),
                request.temperature(),
                request.rainfall(),
                request.humidity(),
                DemandForecastAiRequest.menuCategoryLabel(request.menuCategory()),
                request.mainMenu().strip(),
                request.specialMenu());
    }
}

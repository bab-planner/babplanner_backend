package com.babplanner.backend.demand.client;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.security.ServiceTokenAuthenticationFilter;
import com.babplanner.backend.menu.domain.MealType;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * 별도 프로세스로 떠 있는 AI 서비스를 호출한다.
 *
 * <p>보내는 것은 "무엇을 예측할지"뿐이다. 관리자가 누구인지는 예측에 필요하지
 * 않으므로 넘기지 않는다.
 *
 * <p>과거 관측치 배열을 보내지 않는다. 어느 기간의 무엇을 집계한 값인지
 * 정의된 적이 없고, 정의되지 않은 값을 채워 보내면 받는 쪽은 그것이 의미
 * 있는 입력이라고 믿는다.
 *
 * <p>응답을 그대로 믿지 않는다. 계약을 벗어난 응답은 저장하느니 실패로
 * 끝낸다. 잘못된 예측 인원이 저장되면 그 숫자로 발주가 나간다.
 *
 * <p>다시 보내지 않는다. 관리자가 한 번 누른 실행이 조용히 두 번 처리되면
 * 이력에 같은 실행이 두 줄 남는다.
 */
@Component
public class HttpDemandPredictor implements DemandPredictor {

    private static final Logger log = LoggerFactory.getLogger(HttpDemandPredictor.class);

    static final String PREDICT_PATH = "/internal/v1/ai/demand/predict";

    private final AiRestClientProvider clients;

    public HttpDemandPredictor(AiRestClientProvider clients) {
        this.clients = clients;
    }

    @Override
    public DemandPredictionResult predict(
            String requestId, long siteId, LocalDate serviceDate, MealType mealType) {
        AiDemandResponse response;
        try {
            response = clients.client()
                    .post()
                    .uri(PREDICT_PATH)
                    .header(
                            ServiceTokenAuthenticationFilter.SERVICE_TOKEN_HEADER,
                            clients.serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "requestId", requestId,
                            "siteId", siteId,
                            "serviceDate", serviceDate.toString(),
                            "mealType", mealType.name()))
                    .retrieve()
                    .body(AiDemandResponse.class);
        } catch (RestClientException exception) {
            // 상태 코드는 남기되 본문이나 헤더는 남기지 않는다. 토큰이 로그로
            // 새면 안 된다.
            log.warn("수요 예측 요청이 실패했습니다. requestId={}", requestId);
            throw new AiServiceException("예측 서비스를 호출하지 못했습니다.", exception);
        }

        return validated(requestId, response);
    }

    private DemandPredictionResult validated(String requestId, AiDemandResponse response) {
        if (response == null) {
            throw new AiServiceException("예측 서비스가 빈 응답을 돌려줬습니다.");
        }
        if (!requestId.equals(response.requestId())) {
            // 다른 요청의 결과를 이 실행으로 저장하면 안 된다.
            throw new AiServiceException("예측 응답의 요청 식별자가 일치하지 않습니다.");
        }
        if (response.predictedPeople() == null || response.predictedPeople() < 0) {
            // 음수 인원은 예측일 수 없다.
            throw new AiServiceException("예측 인원이 허용 범위를 벗어났습니다.");
        }
        if (response.modelVersion() == null || response.modelVersion().isBlank()) {
            // 어느 구현이 낸 값인지 모르면 나중에 이 행을 해석할 수 없다.
            throw new AiServiceException("예측 응답에 모델 버전이 없습니다.");
        }

        return new DemandPredictionResult(response.predictedPeople(), response.modelVersion());
    }

    /** OpenAPI AIDemandResponse. */
    record AiDemandResponse(String requestId, Integer predictedPeople, String modelVersion) {}
}

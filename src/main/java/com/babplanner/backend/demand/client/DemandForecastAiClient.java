package com.babplanner.backend.demand.client;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.security.ServiceTokenAuthenticationFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * FastAPI 식수 예측(demand-forecast) 호출.
 *
 * <p>날씨·학사·메뉴 조건을 보내고, AI 서버가 저장된 모델로 다음 날 중식 식수를
 * 예측한다.
 *
 * <p>주소, 토큰, 시간 제한은 {@link AiRestClientProvider}의 것을 그대로 쓴다.
 * AI 서비스는 식수 예측만 제공하므로 설정은 이 한 곳이다.
 *
 * <p>응답을 그대로 믿지 않는다. 계약을 벗어난 응답은 관리자에게 보여 주느니
 * 실패로 끝낸다. 예측 인원이 없는데 0으로 보이거나, 합성데이터 모델인지 모르는
 * 채로 숫자가 나가면 그 숫자로 식재료를 주문하게 된다.
 *
 * <p>다시 보내지 않는다. 실패는 실패로 알린다.
 */
@Component
public class DemandForecastAiClient {

    private static final Logger log = LoggerFactory.getLogger(DemandForecastAiClient.class);

    static final String PREDICT_PATH = "/internal/v1/ai/demand-forecast/predict";
    static final String STATUS_PATH = "/internal/v1/ai/demand-forecast/status";

    /** 이 계약은 중식만 예측한다. */
    static final String LUNCH = "LUNCH";

    private final AiRestClientProvider clients;

    public DemandForecastAiClient(AiRestClientProvider clients) {
        this.clients = clients;
    }

    /**
     * @throws AiServiceException 호출하지 못했거나(연결 실패, 시간 초과, 4xx/5xx)
     *     응답이 계약을 벗어난 경우
     */
    public DemandForecastAiResponse predict(DemandForecastAiRequest request) {
        long started = System.nanoTime();
        DemandForecastAiResponse response;
        try {
            response = clients.client()
                    .post()
                    .uri(PREDICT_PATH)
                    .header(
                            ServiceTokenAuthenticationFilter.SERVICE_TOKEN_HEADER,
                            clients.serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DemandForecastAiResponse.class);
        } catch (RestClientException exception) {
            // 상태와 원인 종류만 남긴다. 요청 본문과 헤더는 남기지 않는다.
            log.warn(
                    "식수 예측 호출 실패. date={} status={} elapsedMs={}",
                    request.date(),
                    describe(exception),
                    elapsedMillis(started));
            throw new AiServiceException("식수 예측 서비스를 호출하지 못했습니다.", exception);
        }

        DemandForecastAiResponse valid = validated(request, response);
        log.info(
                "식수 예측 성공. date={} modelVersion={} synthetic={} elapsedMs={}",
                request.date(),
                valid.model().version(),
                valid.model().syntheticData(),
                elapsedMillis(started));
        return valid;
    }

    /**
     * @throws AiServiceException AI 서버에 닿지 못했거나 응답이 계약을 벗어난 경우.
     *     모델이 없어 쓸 수 없다는 응답은 예외가 아니라 정상 응답이다
     */
    public DemandForecastAiStatus status() {
        long started = System.nanoTime();
        DemandForecastAiStatus status;
        try {
            status = clients.client()
                    .get()
                    .uri(STATUS_PATH)
                    .header(
                            ServiceTokenAuthenticationFilter.SERVICE_TOKEN_HEADER,
                            clients.serviceToken())
                    .retrieve()
                    .body(DemandForecastAiStatus.class);
        } catch (RestClientException exception) {
            log.warn(
                    "식수 예측 상태 조회 실패. status={} elapsedMs={}",
                    describe(exception),
                    elapsedMillis(started));
            throw new AiServiceException("식수 예측 서비스 상태를 확인하지 못했습니다.", exception);
        }
        if (status == null || status.available() == null || status.modelLoaded() == null) {
            throw contractViolation("상태 응답에 available/modelLoaded가 없습니다.", null);
        }
        return status;
    }

    private DemandForecastAiResponse validated(
            DemandForecastAiRequest request, DemandForecastAiResponse response) {
        if (response == null) {
            throw contractViolation("빈 응답", request.date());
        }
        if (!request.date().equals(response.forecastDate())) {
            // 다른 날짜의 결과를 이 요청의 결과로 보여 주면 안 된다.
            throw contractViolation("요청한 날짜와 응답 날짜가 다릅니다", request.date());
        }
        if (!LUNCH.equals(response.mealType())) {
            throw contractViolation("중식 예측이 아닙니다", request.date());
        }
        if (response.predictedMealCount() == null || response.predictedMealCount() < 0) {
            // 음수 식수는 예측일 수 없고, 빠진 값을 0으로 읽으면 "0명"이 된다.
            throw contractViolation("예측 식수가 없거나 음수입니다", request.date());
        }
        DemandForecastAiResponse.Model model = response.model();
        if (model == null || model.version() == null || model.version().isBlank()) {
            // 어느 모델이 낸 값인지 모르면 나중에 이 숫자를 해석할 수 없다.
            throw contractViolation("모델 버전이 없습니다", request.date());
        }
        if (model.syntheticData() == null) {
            // 합성데이터 모델인지 모르는 채로 숫자를 내보내지 않는다.
            throw contractViolation("syntheticData가 없습니다", request.date());
        }
        if (response.disclaimer() == null || response.disclaimer().isBlank()) {
            throw contractViolation("disclaimer가 없습니다", request.date());
        }
        DemandForecastAiResponse.Reference reference = response.reference();
        if (reference == null
                || reference.previousDay() == null
                || reference.sameWeekdayPreviousWeek() == null
                || reference.rolling7DayAverage() == null
                || reference.historyAsOf() == null) {
            throw contractViolation("reference가 없습니다", request.date());
        }
        if (response.warnings() == null) {
            return new DemandForecastAiResponse(
                    response.forecastDate(),
                    response.mealType(),
                    response.predictedMealCount(),
                    reference,
                    response.conditions(),
                    model,
                    List.of(),
                    response.disclaimer());
        }
        return response;
    }

    private static AiServiceException contractViolation(String reason, String date) {
        log.warn("식수 예측 응답이 계약을 벗어났습니다. date={} reason={}", date, reason);
        return new AiServiceException("식수 예측 응답이 계약을 벗어났습니다: " + reason);
    }

    /** 로그용. 응답 본문은 담지 않는다. */
    private static String describe(RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        if (exception instanceof ResourceAccessException && exception.getCause() != null) {
            // 연결 거부, 시간 초과 등. 예외 종류만 남긴다.
            return exception.getCause().getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}

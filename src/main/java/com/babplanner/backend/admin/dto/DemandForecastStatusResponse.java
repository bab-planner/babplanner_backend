package com.babplanner.backend.admin.dto;

import com.babplanner.backend.demand.client.DemandForecastAiStatus;
import java.util.List;

/**
 * 식수 예측 모델 상태. 데모 전에 모델이 떠 있는지 확인하는 용도다.
 *
 * <p>AI 서버에 닿았다면 모델이 없어도 이 응답이다({@code available=false},
 * {@code reason}). AI 서버에 닿지 못하면 이 응답이 아니라 503이다.
 *
 * @param reason 쓸 수 없는 이유. AI 서버가 파일 경로 없이 만든 짧은 문구를 그대로
 *     옮긴다
 */
public record DemandForecastStatusResponse(
        boolean available,
        boolean modelLoaded,
        Boolean historyAvailable,
        String modelName,
        String modelVersion,
        Boolean syntheticData,
        List<String> trainingPeriod,
        String reason,
        List<String> warnings) {

    public static DemandForecastStatusResponse from(DemandForecastAiStatus status) {
        return new DemandForecastStatusResponse(
                status.available(),
                status.modelLoaded(),
                status.historyAvailable(),
                status.modelName(),
                status.modelVersion(),
                status.syntheticData(),
                status.trainingPeriod() == null ? List.of() : List.copyOf(status.trainingPeriod()),
                status.reason(),
                status.warnings() == null ? List.of() : List.copyOf(status.warnings()));
    }
}

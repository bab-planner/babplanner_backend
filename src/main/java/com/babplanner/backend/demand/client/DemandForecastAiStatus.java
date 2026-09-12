package com.babplanner.backend.demand.client;

import java.util.List;

/**
 * FastAPI {@code DemandForecastStatus}.
 *
 * <p>AI 서버는 값이 없는 필드를 응답에서 뺀다. 그래서 전부 nullable이다.
 *
 * @param reason 쓸 수 없는 이유. AI 서버가 파일 경로를 담지 않도록 만든 짧은 문구다
 */
public record DemandForecastAiStatus(
        Boolean available,
        Boolean modelLoaded,
        Boolean historyAvailable,
        String modelName,
        String modelVersion,
        String target,
        Boolean syntheticData,
        List<String> trainingPeriod,
        String reason,
        List<String> warnings) {}

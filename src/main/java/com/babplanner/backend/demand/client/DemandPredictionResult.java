package com.babplanner.backend.demand.client;

/**
 * 검증을 마친 예측 결과.
 *
 * <p>여기까지 온 값은 계약을 통과한 것이다. 저장 코드가 다시 검사하지 않아도
 * 되도록, 검증은 경계에서 한 번만 한다.
 */
public record DemandPredictionResult(int predictedPeople, String modelVersion) {}

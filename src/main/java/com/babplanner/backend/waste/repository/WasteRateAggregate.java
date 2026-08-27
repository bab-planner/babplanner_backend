package com.babplanner.backend.waste.repository;

/**
 * 잔반률 집계 한 줄.
 *
 * <p>표본이 없으면 sampleCount가 0이고 averageWasteRate는 null이다. 이때
 * 평균을 0으로 바꾸지 않는다. "버린 게 없다"와 "잰 적이 없다"는 다른 말이다.
 *
 * @param sampleCount 평균에 실제로 들어간 제출 건수
 * @param averageWasteRate 표본이 없으면 null
 */
public record WasteRateAggregate(Long sampleCount, Double averageWasteRate) {}

package com.babplanner.backend.waste.repository;

/**
 * 메뉴 하나의 잔반률 집계.
 *
 * <p>group by 결과라 sampleCount는 항상 1 이상이고 평균도 null이 아니다.
 */
public record MenuWasteAggregate(
        Long menuId, String menuName, Double averageWasteRate, Long sampleCount) {}

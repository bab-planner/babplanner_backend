package com.babplanner.backend.admin.dto;

import com.babplanner.backend.waste.repository.MenuWasteAggregate;

/**
 * OpenAPI MenuWaste.
 *
 * <p>평균은 DB가 낸 값을 그대로 내보낸다. 보기 좋으라고 자리를 자르면 화면에
 * 보이는 숫자와 실제 값이 달라진다. 반올림은 보는 쪽에서 정한다.
 */
public record MenuWasteResponse(
        Long menuId, String menuName, double averageWasteRate, long sampleCount) {

    public static MenuWasteResponse from(MenuWasteAggregate aggregate) {
        return new MenuWasteResponse(
                aggregate.menuId(),
                aggregate.menuName(),
                aggregate.averageWasteRate(),
                aggregate.sampleCount());
    }
}

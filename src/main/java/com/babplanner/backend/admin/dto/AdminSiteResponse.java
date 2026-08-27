package com.babplanner.backend.admin.dto;

import com.babplanner.backend.site.domain.DiningSite;

/**
 * OpenAPI AdminSite.
 *
 * <p>관리자 화면이 식당을 고를 수 있게 하는 최소 목록이다. 프론트가
 * {@code siteId = 1} 같은 값을 코드에 박지 않으려면 이 목록이 필요하다.
 *
 * <p>{@code code}는 내부 식별용이라 내보내지 않는다. 화면이 보여 줄 이유가
 * 없고, 한 번 나가면 클라이언트가 그 문자열로 분기하기 시작한다.
 */
public record AdminSiteResponse(Long id, String name) {

    public static AdminSiteResponse from(DiningSite site) {
        return new AdminSiteResponse(site.getId(), site.getName());
    }
}

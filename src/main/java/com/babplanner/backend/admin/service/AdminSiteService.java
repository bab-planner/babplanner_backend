package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.AdminSiteResponse;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 식당 목록.
 *
 * <p>읽기 전용이다. 식당을 만들거나 고치는 API는 두지 않는다. 관리자 웹이
 * 필요한 것은 고를 수 있는 목록뿐이고, 식당 정보는 메뉴·혼잡도·예측이 전부
 * 참조하는 값이라 화면에서 손대게 할 이유가 없다.
 */
@Service
public class AdminSiteService {

    private final DiningSiteRepository siteRepository;

    public AdminSiteService(DiningSiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    /** 노출 순서대로. 같은 순서면 id 순이라 목록이 흔들리지 않는다. */
    @Transactional(readOnly = true)
    public List<AdminSiteResponse> getSites() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AdminSiteResponse::from)
                .toList();
    }
}

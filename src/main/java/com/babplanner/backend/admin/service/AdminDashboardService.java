package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.AdminDashboardResponse;
import com.babplanner.backend.congestion.service.CongestionService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드.
 *
 * <p>실제로 저장돼 있는 것만 모은다. 칸은 현재 혼잡도와 오늘 잔반 요약 둘이다.
 * 화면을 채우려고 표본값이나 예시값을 넣지 않는다.
 *
 * <p>"오늘"은 주입된 Clock의 시간대를 따른다. 급식 제공일은 한국 날짜지만
 * 시간대를 코드에 박지 않는 것은, 어느 시간대가 맞는지는 배포가 정할 일이고
 * Clock bean이 그 유일한 지점이기 때문이다.
 */
@Service
public class AdminDashboardService {

    private final CongestionService congestionService;
    private final AdminWasteService adminWasteService;
    private final Clock clock;

    public AdminDashboardService(
            CongestionService congestionService, AdminWasteService adminWasteService, Clock clock) {
        this.congestionService = congestionService;
        this.adminWasteService = adminWasteService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(Long siteId) {
        LocalDate today = LocalDate.now(clock);

        return new AdminDashboardResponse(
                congestionService.findCurrent(siteId).orElse(null),
                adminWasteService.getSummary(today, today));
    }
}

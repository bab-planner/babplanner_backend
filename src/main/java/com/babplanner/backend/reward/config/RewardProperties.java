package com.babplanner.backend.reward.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 리워드 지급 설정.
 *
 * <p>쿠폰 발급 규칙은 아직 확정되지 않은 제품 항목이라 여기에 두지 않는다.
 */
@ConfigurationProperties(prefix = "bapplanner.reward")
public class RewardProperties {

    /** 잔반 인증이 APPROVED가 될 때 지급하는 포인트. 0 이상. */
    private int wasteApprovalPoints = 100;

    public int getWasteApprovalPoints() {
        return wasteApprovalPoints;
    }

    public void setWasteApprovalPoints(int wasteApprovalPoints) {
        if (wasteApprovalPoints < 0) {
            throw new IllegalArgumentException(
                    "bapplanner.reward.waste-approval-points는 0 이상이어야 합니다.");
        }
        this.wasteApprovalPoints = wasteApprovalPoints;
    }
}

package com.babplanner.backend.common.config;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 급식 운영일을 정하는 시간대.
 *
 * <p>{@code Menu.serviceDate} 같은 값은 시각이 아니라 급식소 현지 영업일이다.
 * 그 날짜를 어느 시간대로 끊을지는 배포가 정할 일이라 코드에 박지 않는다.
 *
 * <p>기본값을 두지 않는다. 설정이 없을 때 조용히 UTC로 넘어가면 한국에서는
 * 자정부터 오전 9시까지 하루가 어긋난 채로 동작하고, 그 사이에 관리자가 보는
 * 오늘의 잔반 요약은 전날 것이 된다. 화면에 숫자가 떠 있으니 틀린 줄도
 * 모른다. 그래서 미설정은 기동 실패로 처리한다.
 */
@ConfigurationProperties(prefix = "bapplanner")
public class BusinessTimeProperties {

    /** IANA 시간대 식별자. 예: Asia/Seoul. 비밀 값이 아니다. */
    private String businessZoneId;

    public String getBusinessZoneId() {
        return businessZoneId;
    }

    public void setBusinessZoneId(String businessZoneId) {
        this.businessZoneId = businessZoneId;
    }

    /**
     * 설정된 시간대.
     *
     * @throws IllegalStateException 값이 없거나 해석할 수 없는 경우
     */
    public ZoneId resolvedZone() {
        if (businessZoneId == null || businessZoneId.isBlank()) {
            throw new IllegalStateException(
                    "bapplanner.business-zone-id를 설정해야 합니다. 급식 운영일을 끊는 IANA"
                            + " 시간대이며(예: Asia/Seoul), 값이 없을 때 UTC로 넘어가면 자정"
                            + " 무렵 하루가 어긋난 채로 조용히 동작합니다.");
        }
        try {
            return ZoneId.of(businessZoneId.strip());
        } catch (DateTimeException exception) {
            // 시간대 식별자는 비밀이 아니므로 무엇이 잘못됐는지 그대로 보여 준다.
            throw new IllegalStateException(
                    "bapplanner.business-zone-id를 시간대로 해석할 수 없습니다: "
                            + businessZoneId,
                    exception);
        }
    }
}

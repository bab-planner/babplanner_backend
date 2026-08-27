package com.babplanner.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 간 호출용 공유 토큰.
 *
 * <p>{@code /internal/v1/**} 는 사용자 API가 아니다. CCTV 수집기 같은 다른
 * 프로세스가 부르는 경로이고, 사용자 JWT로는 들어올 수 없어야 한다.
 *
 * <p>기본값을 두지 않는다. 값이 없는데 기동에 성공하면 내부 경로가 어떤
 * 상태로 열려 있는지 아무도 확신할 수 없다.
 *
 * <p>이 토큰은 Spring이 <em>받는</em> 쪽이다. Spring이 AI 서비스를 부를 때
 * 쓰는 토큰({@link AiServiceProperties})과는 다른 비밀 값이다. 하나를
 * 돌려쓰면 한쪽이 새는 순간 양쪽이 같이 뚫린다.
 */
@ConfigurationProperties(prefix = "bapplanner.internal")
public class InternalServiceProperties {

    /** 짧은 토큰은 사실상 없는 것과 같다. */
    public static final int MIN_TOKEN_LENGTH = 32;

    private String serviceToken;

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    /**
     * 검증된 토큰.
     *
     * @throws IllegalStateException 값이 없거나 너무 짧은 경우
     */
    public String requiredToken() {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "bapplanner.internal.service-token 이 필요합니다. 내부 수집 경로를 "
                            + "여는 값이므로 기본값을 두지 않습니다.");
        }
        if (serviceToken.length() < MIN_TOKEN_LENGTH) {
            // 값 자체는 남기지 않는다.
            throw new IllegalStateException(
                    "bapplanner.internal.service-token 은 최소 " + MIN_TOKEN_LENGTH
                            + "자여야 합니다.");
        }
        return serviceToken;
    }
}

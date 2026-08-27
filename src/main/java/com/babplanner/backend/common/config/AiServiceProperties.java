package com.babplanner.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서비스 호출 설정.
 *
 * <p>잔반 분석, 수요 예측, 발주 추천은 모두 같은 FastAPI 프로세스다. 기능마다
 * 주소와 토큰을 따로 두면 셋 중 하나만 다른 곳을 가리키는 배포가 가능해지고,
 * 그 상태는 기동 로그만 봐서는 드러나지 않는다.
 *
 * <p>이 토큰은 Spring이 <em>보내는</em> 쪽이다. 내부 수집 경로가 받는
 * 토큰({@link InternalServiceProperties})과는 다른 비밀 값이다.
 */
@ConfigurationProperties(prefix = "bapplanner.ai")
public class AiServiceProperties {

    /** 예: http://127.0.0.1:8001. 비밀 값이 아니다. */
    private String baseUrl;

    private String serviceToken;

    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 응답 대기 상한.
     *
     * <p>관리자가 누른 실행은 결과가 나올 때까지 기다려도 된다. 다만 무한정
     * 기다리면 요청 스레드가 그대로 묶인다.
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String requiredBaseUrl() {
        return required(baseUrl, "bapplanner.ai.base-url");
    }

    public String requiredServiceToken() {
        return required(serviceToken, "bapplanner.ai.service-token");
    }

    private static String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " 가 필요합니다.");
        }
        return value;
    }
}

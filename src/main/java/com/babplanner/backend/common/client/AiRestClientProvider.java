package com.babplanner.backend.common.client;

import com.babplanner.backend.common.config.AiServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 필요할 때 AI 서비스 클라이언트를 만들어 준다.
 *
 * <p>기동 시점에 만들지 않는 이유가 있다. 수요 예측과 발주 추천은 AI 주소가
 * 설정돼 있지 않아도 조회는 계속 동작해야 한다. 이미 저장된 예측을 보는 일은
 * AI 서비스와 무관하기 때문이다. 주소가 없다고 애플리케이션 전체가 뜨지 않게
 * 만들면, 그 배포에서는 지난 예측조차 볼 수 없다.
 *
 * <p>대신 실행을 시도하는 순간 실패한다. 그 실패는 503으로 나간다.
 */
@Component
public class AiRestClientProvider {

    private final AiServiceProperties properties;

    private volatile RestClient client;

    public AiRestClientProvider(AiServiceProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws AiServiceException 주소나 토큰이 설정되지 않은 경우
     */
    public RestClient client() {
        RestClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                try {
                    client = AiRestClients.create(properties);
                } catch (IllegalStateException exception) {
                    throw new AiServiceException("AI 서비스가 설정되지 않았습니다.", exception);
                }
            }
            return client;
        }
    }

    /**
     * @throws AiServiceException 토큰이 설정되지 않은 경우
     */
    public String serviceToken() {
        try {
            return properties.requiredServiceToken();
        } catch (IllegalStateException exception) {
            throw new AiServiceException("AI 서비스 토큰이 설정되지 않았습니다.", exception);
        }
    }
}

package com.babplanner.backend.common.client;

import com.babplanner.backend.common.config.AiServiceProperties;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스용 RestClient 생성.
 *
 * <p>잔반/수요/발주가 같은 주소와 같은 시간 제한을 쓴다. 세 곳에서 각자
 * 만들면 한 곳만 timeout이 달라져도 알아차리기 어렵다.
 *
 * <p>재시도를 붙이지 않는다. 수요 예측과 발주 추천은 관리자가 명시적으로
 * 누른 실행이라, 실패했으면 실패했다고 알리는 편이 맞다. 조용히 다시
 * 보내면 같은 요청이 두 번 처리될 수 있다.
 */
public final class AiRestClients {

    private AiRestClients() {}

    public static RestClient create(AiServiceProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.requiredBaseUrl())
                .requestFactory(
                        requestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }
}

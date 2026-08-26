package com.babplanner.backend.waste.config;

import com.babplanner.backend.waste.analysis.FakeWasteAnalyzer;
import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 분석기 구현 선택.
 *
 * <p>{@code bapplanner.waste.analyzer-mode} 로 정확히 하나만 고른다. 기본값이
 * 없으므로 아무것도 고르지 않으면 기동에 실패한다. fake와 http가 같이
 * 등록되는 조합은 없다.
 */
@Configuration
public class WasteAnalyzerConfig {

    private static final Logger log = LoggerFactory.getLogger(WasteAnalyzerConfig.class);

    /**
     * 개발용 분석기.
     *
     * <p>이미지를 판독하지 않고 고정값을 돌려준다. 운영에서 자동 선택되면
     * 분석하지 않은 결과가 APPROVED로 나가므로 명시했을 때만 등록한다.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "bapplanner.waste",
            name = "analyzer-mode",
            havingValue = "fake")
    public WasteAnalyzer fakeWasteAnalyzer(WasteImageStorage storage) {
        // 어떤 분석기로 떴는지 기동 로그에 남긴다. 설정이 어딘가에서 덮어써져도
        // 이 줄을 보면 알 수 있다.
        log.info("잔반 분석기: 개발용 fixture. 이미지를 판독하지 않는다.");
        return new FakeWasteAnalyzer(storage);
    }

    /** 별도 프로세스로 떠 있는 분석 서비스를 호출하는 구현. */
    @Bean
    @ConditionalOnProperty(
            prefix = "bapplanner.waste",
            name = "analyzer-mode",
            havingValue = "http")
    public WasteAnalyzer httpWasteAnalyzer(WasteProperties properties, WasteImageStorage storage) {
        String baseUrl = required(properties.getAiBaseUrl(), "bapplanner.waste.ai-base-url");
        String token =
                required(properties.getAiServiceToken(), "bapplanner.waste.ai-service-token");

        // 주소는 비밀이 아니다. 토큰은 남기지 않는다.
        log.info("잔반 분석기: 분석 서비스 {}", baseUrl);
        return new HttpWasteAnalyzer(
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(requestFactory(
                                properties.getAiConnectTimeout(), properties.getAiReadTimeout()))
                        .build(),
                storage,
                token);
    }

    /**
     * 응답을 기다리는 것은 분석 전용 작업 스레드다. 요청 스레드가 아니므로
     * 동기 호출로 충분하고, 이것 때문에 WebFlux를 끌어들이지 않는다.
     */
    private static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }

    /** 어떤 분석기도 구성되지 않은 경우. 조용히 넘어가지 않는다. */
    @Bean
    @ConditionalOnMissingBean(WasteAnalyzer.class)
    public WasteAnalyzer unconfiguredWasteAnalyzer() {
        throw new IllegalStateException(
                "WasteAnalyzer가 구성되지 않았습니다. bapplanner.waste.analyzer-mode 를 "
                        + "http 또는 fake 로 명시하세요.");
    }

    private static String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    propertyName + " 가 필요합니다. analyzer-mode=http 를 쓰려면 값을 주입하세요.");
        }
        return value;
    }
}

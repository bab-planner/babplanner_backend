package com.babplanner.backend.waste.config;

import com.babplanner.backend.common.client.AiRestClients;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.waste.analysis.FakeWasteAnalyzer;
import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /**
     * 별도 프로세스로 떠 있는 분석 서비스를 호출하는 구현.
     *
     * <p>주소와 토큰은 {@code bapplanner.ai}에서 온다. 잔반 전용 주소를 따로
     * 두면 수요/발주와 다른 곳을 가리키는 배포가 가능해진다.
     *
     * <p>http를 명시했으면서 주소가 없으면 기동에 실패한다. 여기서 조용히
     * 넘어가면 분석이 되는 줄 알고 배포하게 된다.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "bapplanner.waste",
            name = "analyzer-mode",
            havingValue = "http")
    public WasteAnalyzer httpWasteAnalyzer(
            AiServiceProperties aiProperties, WasteImageStorage storage) {
        String baseUrl = aiProperties.requiredBaseUrl();
        String token = aiProperties.requiredServiceToken();

        // 주소는 비밀이 아니다. 토큰은 남기지 않는다.
        log.info("잔반 분석기: 분석 서비스 {}", baseUrl);
        return new HttpWasteAnalyzer(AiRestClients.create(aiProperties), storage, token);
    }

    /** 어떤 분석기도 구성되지 않은 경우. 조용히 넘어가지 않는다. */
    @Bean
    @ConditionalOnMissingBean(WasteAnalyzer.class)
    public WasteAnalyzer unconfiguredWasteAnalyzer() {
        throw new IllegalStateException(
                "WasteAnalyzer가 구성되지 않았습니다. bapplanner.waste.analyzer-mode 를 "
                        + "http 또는 fake 로 명시하세요.");
    }

}

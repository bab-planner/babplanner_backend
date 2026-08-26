package com.babplanner.backend.waste.config;

import com.babplanner.backend.waste.analysis.FakeWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 분석기 구현 선택.
 *
 * <p>FakeWasteAnalyzer는 이미지를 판독하지 않고 고정값을 돌려준다. 이것이
 * 운영에서 자동 선택되면 분석하지 않은 결과가 APPROVED로 나가므로,
 * {@code bapplanner.waste.analyzer-mode=fake} 를 명시했을 때만 등록한다.
 */
@Configuration
public class WasteAnalyzerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "bapplanner.waste",
            name = "analyzer-mode",
            havingValue = "fake")
    public WasteAnalyzer fakeWasteAnalyzer(WasteImageStorage storage) {
        return new FakeWasteAnalyzer(storage);
    }

    /** 어떤 분석기도 구성되지 않은 경우. 조용히 넘어가지 않는다. */
    @Bean
    @ConditionalOnMissingBean(WasteAnalyzer.class)
    public WasteAnalyzer unconfiguredWasteAnalyzer() {
        throw new IllegalStateException(
                "WasteAnalyzer가 구성되지 않았습니다. 운영에서는 실제 분석 어댑터를 등록하고, "
                        + "로컬/개발에서는 bapplanner.waste.analyzer-mode=fake 를 명시하세요.");
    }
}

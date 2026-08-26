package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.waste.analysis.FakeWasteAnalyzer;
import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.config.WasteAnalyzerConfig;
import com.babplanner.backend.waste.config.WasteProperties;
import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 분석기 구현 선택.
 *
 * <p>둘 중 정확히 하나만 뜨는지, 아무것도 고르지 않으면 기동이 실패하는지 본다.
 */
class WasteAnalyzerConfigTest {

    @TempDir Path storageRoot;

    private ApplicationContextRunner runner() {
        return runner(new WasteProperties());
    }

    private ApplicationContextRunner runner(WasteProperties properties) {
        WasteImageStorage storage = new LocalWasteImageStorage(storageRoot);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of())
                .withUserConfiguration(WasteAnalyzerConfig.class)
                .withBean(WasteImageStorage.class, () -> storage)
                .withBean(WasteProperties.class, () -> properties);
    }

    private WasteProperties properties(String mode, String baseUrl, String token) {
        WasteProperties properties = new WasteProperties();
        properties.setAnalyzerMode(mode);
        properties.setAiBaseUrl(baseUrl);
        properties.setAiServiceToken(token);
        return properties;
    }

    @Test
    @DisplayName("fake 모드는 개발용 분석기만 등록한다")
    void fakeModeRegistersOnlyTheFixture() {
        runner()
                .withPropertyValues("bapplanner.waste.analyzer-mode=fake")
                .run(context -> {
                    assertThat(context).hasSingleBean(WasteAnalyzer.class);
                    assertThat(context.getBean(WasteAnalyzer.class))
                            .isInstanceOf(FakeWasteAnalyzer.class);
                });
    }

    @Test
    @DisplayName("http 모드는 분석 서비스 어댑터만 등록한다")
    void httpModeRegistersOnlyTheAdapter() {
        runner(properties(
                        "http",
                        "http://127.0.0.1:8001",
                        "test-only-token-not-a-real-secret"))
                .withPropertyValues("bapplanner.waste.analyzer-mode=http")
                .run(context -> {
                    assertThat(context).hasSingleBean(WasteAnalyzer.class);
                    assertThat(context.getBean(WasteAnalyzer.class))
                            .isInstanceOf(HttpWasteAnalyzer.class);
                });
    }

    @Test
    @DisplayName("모드를 고르지 않으면 기동에 실패한다")
    void missingModeFailsFast() {
        runner().run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("알 수 없는 모드도 기동에 실패한다")
    void unknownModeFailsFast() {
        runner()
                .withPropertyValues("bapplanner.waste.analyzer-mode=magic")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("http 모드인데 주소가 없으면 기동에 실패한다")
    void httpModeWithoutBaseUrlFailsFast() {
        WasteAnalyzerConfig config = new WasteAnalyzerConfig();
        WasteImageStorage storage = new LocalWasteImageStorage(storageRoot);

        assertThatThrownBy(() -> config.httpWasteAnalyzer(
                        properties("http", null, "test-only-token-not-a-real-secret"), storage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai-base-url");
    }

    @Test
    @DisplayName("http 모드인데 토큰이 없으면 기동에 실패한다")
    void httpModeWithoutTokenFailsFast() {
        WasteAnalyzerConfig config = new WasteAnalyzerConfig();
        WasteImageStorage storage = new LocalWasteImageStorage(storageRoot);

        assertThatThrownBy(() -> config.httpWasteAnalyzer(
                        properties("http", "http://127.0.0.1:8001", "  "), storage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai-service-token");
    }

    @Test
    @DisplayName("주소와 토큰에 기본값이 없다")
    void aiSettingsHaveNoDefaults() {
        WasteProperties properties = new WasteProperties();

        assertThat(properties.getAiBaseUrl()).isNull();
        assertThat(properties.getAiServiceToken()).isNull();
        assertThat(properties.getAnalyzerMode()).isNull();
    }

    @Test
    @DisplayName("시간 제한에 합리적인 기본값이 있다")
    void timeoutsHaveDefaults() {
        WasteProperties properties = new WasteProperties();

        assertThat(properties.getAiConnectTimeout().toSeconds()).isEqualTo(5);
        assertThat(properties.getAiReadTimeout().toSeconds()).isEqualTo(30);
    }
}

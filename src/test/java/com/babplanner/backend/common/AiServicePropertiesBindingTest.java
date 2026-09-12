package com.babplanner.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.config.AiServiceProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 서버 주소와 토큰이 이름 그대로 실제로 묶이는지.
 *
 * <p>테스트 classpath의 application.yml은 운영 파일을 가린다. 그래서 환경 변수 →
 * 운영 application.yml placeholder → 설정 객체로 이어지는 길은 운영 파일을 직접 읽어
 * 확인하고, 컨텍스트가 만든 bean은 따로 확인한다.
 */
@SpringBootTest(properties = {
    "bapplanner.ai.base-url=http://ai-server:8000",
    "bapplanner.ai.service-token=binding-test-token-not-a-real-secret-0123456789"
})
@ActiveProfiles("test")
class AiServicePropertiesBindingTest {

    @Autowired private AiServiceProperties properties;

    @Test
    @DisplayName("컨텍스트의 설정 bean에 주소와 토큰이 묶인다")
    void contextBindsBaseUrlAndToken() {
        assertThat(properties.getBaseUrl()).isEqualTo("http://ai-server:8000");
        assertThat(properties.requiredServiceToken())
                .isEqualTo("binding-test-token-not-a-real-secret-0123456789");
    }

    private static AiServiceProperties bindFromMainYaml(Map<String, Object> environmentVariables)
            throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("fake-env", environmentVariables));
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("main", new FileSystemResource("src/main/resources/application.yml"));
        yaml.forEach(source -> environment.getPropertySources().addLast(source));
        return Binder.get(environment)
                .bind("bapplanner.ai", AiServiceProperties.class)
                .orElseGet(AiServiceProperties::new);
    }

    @Test
    @DisplayName("AI_SERVICE_BASE_URL / AI_SERVICE_TOKEN 환경 변수가 설정으로 들어간다")
    void environmentVariablesReachTheProperties() throws IOException {
        AiServiceProperties bound = bindFromMainYaml(Map.of(
                "AI_SERVICE_BASE_URL", "http://127.0.0.1:8001",
                "AI_SERVICE_TOKEN", "env-token-not-a-real-secret-0123456789abcdef"));

        assertThat(bound.requiredBaseUrl()).isEqualTo("http://127.0.0.1:8001");
        assertThat(bound.requiredServiceToken())
                .isEqualTo("env-token-not-a-real-secret-0123456789abcdef");
        // 시간 제한은 잔반·수요·발주가 함께 쓰는 값이다.
        assertThat(bound.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(bound.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("환경 변수가 없으면 주소도 토큰도 없고, 호출하는 순간 실패한다")
    void missingEnvironmentFailsOnUseNotAtStartup() throws IOException {
        AiServiceProperties bound = bindFromMainYaml(Map.of());

        // 코드에 기본 주소나 토큰이 숨어 있지 않다.
        assertThat(bound.getBaseUrl()).isNullOrEmpty();
        assertThat(bound.getServiceToken()).isNullOrEmpty();
        AiRestClientProvider provider = new AiRestClientProvider(bound);
        assertThatThrownBy(provider::client).isInstanceOf(AiServiceException.class);
        assertThatThrownBy(provider::serviceToken).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("설정 객체를 문자열로 찍어도 토큰이 나오지 않는다")
    void propertiesToStringDoesNotLeakTheToken() {
        assertThat(properties.toString())
                .doesNotContain("binding-test-token-not-a-real-secret-0123456789");
    }
}

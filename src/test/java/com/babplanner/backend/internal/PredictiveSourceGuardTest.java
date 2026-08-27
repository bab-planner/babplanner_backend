package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.common.config.InternalServiceProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "이렇게는 하지 않기로 한 것"을 소스에서 고정한다.
 *
 * <p>예측과 발주는 숫자가 그대로 실제 발주로 이어진다. 어디선가 그럴듯한
 * 값을 만들기 시작하면 실행 테스트로 잡히기 전에 배포될 수 있다.
 */
class PredictiveSourceGuardTest {

    private static final Path MAIN = Path.of("src/main/java");

    private record Source(String path, String code) {}

    /** 주석을 빼고 실제 코드만 남긴다. 설명 문구가 오탐을 만들지 않도록. */
    private List<Source> sources() throws IOException {
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> kept = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.stripLeading();
                    if (trimmed.startsWith("//")
                            || trimmed.startsWith("*")
                            || trimmed.startsWith("/*")) {
                        continue;
                    }
                    kept.add(line);
                }
                sources.add(
                        new Source(path.toString().replace('\\', '/'), String.join("\n", kept)));
            }
        }
        return sources;
    }

    @Test
    @DisplayName("백엔드가 예측이나 추천 숫자를 스스로 만들지 않는다")
    void noLocalPredictionImplementation() throws IOException {
        for (Source source : sources()) {
            // 이런 구현이 있으면 AI 서비스가 꺼져 있어도 화면에는 값이 뜬다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("FakeDemandPredictor")
                    .doesNotContain("FakeDemandPredictionService")
                    .doesNotContain("FixtureDemandPredictor")
                    .doesNotContain("FakeProcurementRecommender")
                    .doesNotContain("FakeProcurementRecommendationService")
                    .doesNotContain("FixtureProcurementRecommender");
        }
    }

    @Test
    @DisplayName("학습이나 ML 라이브러리를 들이지 않았다")
    void noMachineLearningDependency() throws IOException {
        String build = Files.readString(Path.of("build.gradle.kts"));

        for (String forbidden :
                List.of("djl", "tensorflow", "onnx", "smile", "weka", "deeplearning4j", "tribuo")) {
            assertThat(build).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("인원수로 혼잡 단계를 지어내지 않는다")
    void congestionLevelIsNeverDerivedFromPeopleCount() throws IOException {
        for (Source source : sources()) {
            if (!source.path().contains("/congestion/")) {
                continue;
            }
            // 임계값은 식당 규모와 회전율에 따라 다르고 정해진 적이 없다.
            // 지어낸 임계값으로 만든 "여유"는 아무도 설명할 수 없다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("CongestionLevel.CROWDED")
                    .doesNotContain("CongestionLevel.VERY_CROWDED")
                    .doesNotContain("peopleCount >")
                    .doesNotContain("peopleCount <");
        }
    }

    @Test
    @DisplayName("식단 구성 항목을 식재료로 바꾸지 않는다")
    void menuItemsAreNeverTreatedAsIngredients() throws IOException {
        for (Source source : sources()) {
            if (!source.path().contains("/procurement/") && !source.path().contains("Procurement")) {
                continue;
            }
            // Menu.items에는 조리된 음식 이름이 들어갈 수 있고 구매 단위도
            // 수량도 없다. ProcurementRun.getItems()는 자기 추천 항목이므로
            // 이름만 보고 막지 않고, 식단을 실제로 읽는지를 본다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("MenuRepository")
                    .doesNotContain("menu.domain.Menu;")
                    .doesNotContain("menu.getItems")
                    .doesNotContain("menu.getName");
        }
    }

    @Test
    @DisplayName("AI로 사용자 정보를 보내지 않는다")
    void noUserDataLeavesForTheAiService() throws IOException {
        for (Source source : sources()) {
            if (!source.path().contains("/client/")) {
                continue;
            }
            for (String forbidden :
                    List.of("getPhone", "getStudentId", "getUserId", "userId", "AuthenticatedUser")) {
                assertThat(source.code()).as("%s: %s", source.path(), forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("AI 호출에 재시도를 붙이지 않는다")
    void noRetryAroundAiCalls() throws IOException {
        for (Source source : sources()) {
            if (!source.path().contains("/client/")) {
                continue;
            }
            // 관리자가 한 번 누른 실행이 조용히 두 번 처리되면 이력에 같은
            // 실행이 두 줄 남는다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("@Retryable")
                    .doesNotContain("RetryTemplate")
                    .doesNotContain("for (int attempt")
                    .doesNotContain("while (attempt");
        }
    }

    @Test
    @DisplayName("대시보드에 fixture 결과를 연결하지 않았다")
    void dashboardIsStillNotWiredToPredictions() throws IOException {
        String dashboard = Files.readString(
                MAIN.resolve("com/babplanner/backend/admin/service/AdminDashboardService.java"));

        // 대시보드에는 mealType이 없다. 어느 끼니의 예측을 한 숫자로 보여
        // 줄지 정해진 적이 없으므로 아무거나 고르지 않는다.
        assertThat(dashboard)
                .doesNotContain("DemandPredictionRepository")
                .doesNotContain("ProcurementRunRepository");
    }

    @Test
    @DisplayName("내부 토큰과 AI 토큰이 다른 설정이다")
    void inboundAndOutboundTokensAreSeparate() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        // 하나를 돌려쓰면 한쪽이 새는 순간 양쪽이 같이 뚫린다.
        assertThat(yaml).contains("service-token: ${BAPPLANNER_INTERNAL_SERVICE_TOKEN:}");
        assertThat(yaml).contains("service-token: ${AI_SERVICE_TOKEN:}");
    }

    @Test
    @DisplayName("내부 토큰에 기본값이 없다")
    void internalTokenHasNoDefault() {
        InternalServiceProperties properties = new InternalServiceProperties();

        assertThat(properties.getServiceToken()).isNull();
        assertThatThrownBy(properties::requiredToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bapplanner.internal.service-token");
    }

    @Test
    @DisplayName("짧은 내부 토큰은 기동에 실패한다")
    void shortInternalTokenFailsFast() {
        InternalServiceProperties properties = new InternalServiceProperties();
        properties.setServiceToken("short");

        assertThatThrownBy(properties::requiredToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("토큰 실패 메시지가 값을 흘리지 않는다")
    void tokenFailureLeaksNothing() {
        String secret = "x".repeat(InternalServiceProperties.MIN_TOKEN_LENGTH - 1);
        InternalServiceProperties properties = new InternalServiceProperties();
        properties.setServiceToken(secret);

        assertThatThrownBy(properties::requiredToken).hasMessageNotContaining(secret);
    }

    @Test
    @DisplayName("토큰을 로그로 남기지 않는다")
    void tokensAreNeverLogged() throws IOException {
        for (Source source : sources()) {
            for (String line : source.code().split("\n")) {
                if (!line.contains("log.")) {
                    continue;
                }
                assertThat(line)
                        .as("%s", source.path())
                        .doesNotContain("serviceToken")
                        .doesNotContain("getServiceToken")
                        .doesNotContain("SERVICE_TOKEN_HEADER");
            }
        }
    }

    @Test
    @DisplayName("서비스 토큰을 상수 시간으로 비교한다")
    void serviceTokenUsesConstantTimeComparison() throws IOException {
        String filter = Files.readString(MAIN.resolve(
                "com/babplanner/backend/common/security/ServiceTokenAuthenticationFilter.java"));

        // equals는 첫 다른 바이트에서 멈춘다. 그 시간 차이로 토큰을 한 글자씩
        // 맞춰 나갈 수 있다.
        assertThat(filter).contains("MessageDigest.isEqual");
        assertThat(filter).doesNotContain("presented.equals");
        assertThat(filter).doesNotContain(".equals(properties.getServiceToken())");
    }

    @Test
    @DisplayName("내부 권한이 사람 역할과 섞이지 않는다")
    void internalAuthorityIsNotARole() throws IOException {
        String security = Files.readString(
                MAIN.resolve("com/babplanner/backend/common/security/SecurityConfig.java"));

        // ROLE_ 접두사를 쓰면 hasRole 규칙과 이름 공간이 겹친다.
        assertThat(security).contains("/internal/v1/**");
        assertThat(security).contains("hasAuthority(");
        assertThat(security).doesNotContain("hasRole(\"INTERNAL_SERVICE\")");
    }

    @Test
    @DisplayName("서비스 날짜를 서버 시간대로 다시 해석하지 않는다")
    void serviceDateIsNeverReinterpreted() throws IOException {
        for (Source source : sources()) {
            if (!source.path().contains("/demand/") && !source.path().contains("/procurement/")) {
                continue;
            }
            // 요청에 적힌 날짜와 저장된 날짜가 다르면 아무도 설명할 수 없다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("LocalDate.now(")
                    .doesNotContain("atZone")
                    .doesNotContain("ZoneId");
        }
    }
}

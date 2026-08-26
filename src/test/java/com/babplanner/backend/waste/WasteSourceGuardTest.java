package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이번 단계에서 "만들지 않기로 한 것"을 소스 수준에서 고정한다.
 *
 * <p>부재 조건은 실행 테스트로 잡기 어려워 소스를 직접 훑는다.
 */
class WasteSourceGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path WASTE = MAIN.resolve("com/babplanner/backend/waste");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private record Source(String path, String code) {}

    /** 주석을 제외한 실제 코드만 남긴다. 설명 문구가 오탐을 만들지 않도록. */
    private List<Source> sourcesUnder(Path base) throws IOException {
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base)) {
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
                sources.add(new Source(path.toString().replace('\\', '/'), String.join("\n", kept)));
            }
        }
        return sources;
    }

    @Test
    @DisplayName("리워드 도메인을 잔반 코드가 건드리지 않는다")
    void wasteNeverTouchesReward() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("backend.reward")
                    .doesNotContain("RewardAccount")
                    .doesNotContain("RewardTransaction")
                    .doesNotContain("CouponRepository")
                    .doesNotContain("new Coupon(");
        }
    }

    @Test
    @DisplayName("Controller가 직접 스레드를 만들지 않는다")
    void noManualThreads() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("CompletableFuture")
                    .doesNotContain("new Thread(")
                    .doesNotContain("Executors.new");
        }
    }

    @Test
    @DisplayName("외부 AI 서버나 내부 전용 endpoint를 호출하지 않는다")
    void noExternalAiCalls() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("/internal/v1")
                    .doesNotContain("api.openai.com")
                    .doesNotContain("RestTemplate")
                    .doesNotContain("WebClient")
                    .doesNotContain("ProcessBuilder")
                    .doesNotContain("Runtime.getRuntime");
        }
    }

    @Test
    @DisplayName("로컬 저장소와 개발용 분석기가 자동 등록되지 않는다")
    void devImplementationsAreNotComponentScanned() throws IOException {
        for (String name : List.of(
                "storage/LocalWasteImageStorage.java", "analysis/FakeWasteAnalyzer.java")) {
            String code = Files.readString(WASTE.resolve(name));
            assertThat(code)
                    .as("%s", name)
                    .doesNotContain("@Component")
                    .doesNotContain("@Service")
                    .doesNotContain("@Repository");
        }
    }

    @Test
    @DisplayName("구현 선택 property에 기본값이 없다")
    void storageAndAnalyzerHaveNoDefault() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application.yml"));

        // ${VAR:} 는 미설정 시 빈 값이라 어떤 구현도 선택되지 않는다는 뜻이다.
        assertThat(yaml).contains("storage-mode: ${WASTE_STORAGE_MODE:}");
        assertThat(yaml).contains("analyzer-mode: ${WASTE_ANALYZER_MODE:}");
        assertThat(yaml).doesNotContain("storage-mode: local");
        assertThat(yaml).doesNotContain("analyzer-mode: fake");
    }

    @Test
    @DisplayName("업로드 크기 상한이 설정에 있다")
    void multipartLimitIsConfigured() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application.yml"));

        assertThat(yaml).contains("max-file-size");
        assertThat(yaml).contains("max-request-size");
    }

    @Test
    @DisplayName("verificationToken을 저장하거나 로그로 남기지 않는다")
    void verificationTokenIsNeverKept() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            for (String line : source.code().split("\n")) {
                if (!line.contains("verificationToken")) {
                    continue;
                }
                assertThat(line)
                        .as("%s: %s", source.path(), line.strip())
                        .doesNotContain("log.")
                        .doesNotContain("@Column")
                        .doesNotContain("save(");
            }
        }
    }

    @Test
    @DisplayName("응답 DTO가 저장소 키를 담지 않는다")
    void responseDtoHidesStorageKey() throws IOException {
        Source dto = sourcesUnder(WASTE.resolve("dto")).stream()
                .filter(source -> source.path().endsWith("WasteSubmissionResponse.java"))
                .findFirst()
                .orElseThrow();

        assertThat(dto.code())
                .doesNotContain("imageStorageKey")
                .doesNotContain("getImageStorageKey")
                .doesNotContain("userId")
                .doesNotContain("capturedAt");
    }

    @Test
    @DisplayName("object storage나 메시지 브로커 의존성을 추가하지 않았다")
    void noNewInfrastructureDependencies() throws IOException {
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertThat(build)
                .doesNotContain("aws")
                .doesNotContain("s3")
                .doesNotContain("kafka")
                .doesNotContain("redis")
                .doesNotContain("amqp");
    }

    @Test
    @DisplayName("잔반 관리자 API를 만들지 않았다")
    void noAdminApi() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            assertThat(source.code()).as("%s", source.path()).doesNotContain("/admin");
        }
    }
}

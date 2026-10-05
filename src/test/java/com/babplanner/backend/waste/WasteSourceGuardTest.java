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
                    // 잔반 코드는 RewardService/RewardProperties 경유로만 지급한다.
                    .doesNotContain("backend.reward.repository")
                    .doesNotContain("backend.reward.domain")
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
    @DisplayName("외부 AI 제공자를 직접 호출하거나 프로세스를 띄우지 않는다")
    void noExternalAiCalls() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("api.openai.com")
                    .doesNotContain("api.anthropic.com")
                    .doesNotContain("RestTemplate")
                    .doesNotContain("WebClient")
                    .doesNotContain("ProcessBuilder")
                    .doesNotContain("Runtime.getRuntime");
        }
    }

    @Test
    @DisplayName("로컬 저장소가 자동 등록되지 않는다")
    void localStorageIsNotComponentScanned() throws IOException {
        String code = Files.readString(WASTE.resolve("storage/LocalWasteImageStorage.java"));
        assertThat(code)
                .doesNotContain("@Component")
                .doesNotContain("@Service")
                .doesNotContain("@Repository");
    }

    @Test
    @DisplayName("저장소 구현 선택 property에 기본값이 없다")
    void storageHasNoDefault() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application.yml"));

        assertThat(yaml).contains("storage-mode: ${WASTE_STORAGE_MODE:}");
        assertThat(yaml).doesNotContain("storage-mode: local");
    }

    @Test
    @DisplayName("백엔드 어디에도 AI 잔반 분석 흔적이 없다")
    void noAiWasteAnalysisAnywhere() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("WasteAnalyzer")
                    .doesNotContain("/internal/v1/ai/waste")
                    .doesNotContain("analyzer-mode")
                    .doesNotContain("WasteProcessingDispatcher");
        }
        for (String name : List.of("application.yml", "application-local.yml")) {
            assertThat(Files.readString(RESOURCES.resolve(name)))
                    .as(name)
                    .doesNotContain("analyzer")
                    .doesNotContain("WASTE_ANALYZER_MODE");
        }
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
    @DisplayName("포화 시 호출 스레드로 되돌리거나 조용히 버리는 정책이 없다")
    void noCallerRunsOrDiscardPolicy() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("CallerRunsPolicy")
                    .doesNotContain("DiscardPolicy")
                    .doesNotContain("DiscardOldestPolicy");
        }
    }

    @Test
    @DisplayName("분석 실행 경로가 dispatcher 하나뿐이다")
    void onlyDispatcherStartsProcessing() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            if (source.path().endsWith("WasteProcessingDispatcher.java")
                    || source.path().endsWith("WasteSubmissionProcessor.java")) {
                continue;
            }
            assertThat(source.code())
                    .as("%s 가 processor를 직접 실행한다", source.path())
                    .doesNotContain("processor.process(")
                    .doesNotContain(".process(");
        }
    }

    @Test
    @DisplayName("운영 설정이 동기 실행을 켜지 않는다")
    void productionDoesNotRunAnalysisInline() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application.yml"));

        assertThat(yaml).doesNotContain("async-enabled: false");
        assertThat(Files.readString(RESOURCES.resolve("application-local.yml")))
                .doesNotContain("async-enabled: false");
    }

    @Test
    @DisplayName("실패 사유를 응답 필드로 노출하지 않는다")
    void noFailureReasonField() throws IOException {
        for (Source source : sourcesUnder(WASTE)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("failureReason")
                    .doesNotContain("errorDetail");
        }
    }
}

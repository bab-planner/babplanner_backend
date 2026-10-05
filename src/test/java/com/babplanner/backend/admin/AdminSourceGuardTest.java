package com.babplanner.backend.admin;

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
 * "이렇게는 하지 않기로 한 것"을 소스에서 고정한다.
 *
 * <p>권한이 열려 버리는 실수는 실행 테스트로 잡히기 전에 배포될 수 있다.
 */
class AdminSourceGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path ADMIN = MAIN.resolve("com/babplanner/backend/admin");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private record Source(String path, String code) {}

    /** 주석을 빼고 실제 코드만 남긴다. 설명 문구가 오탐을 만들지 않도록. */
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
    @DisplayName("관리 경로를 공개하지 않는다")
    void adminIsNeverPublic() throws IOException {
        String security = Files.readString(
                MAIN.resolve("com/babplanner/backend/common/security/SecurityConfig.java"));

        assertThat(security).contains("\"/api/v1/admin/**\"");
        assertThat(security).contains("hasRole(\"ADMIN\")");
        // permitAll 목록에 admin 경로가 들어가면 관리 API가 통째로 열린다.
        for (String line : security.split("\n")) {
            if (line.contains("permitAll")) {
                assertThat(line).doesNotContain("admin");
            }
        }
    }

    @Test
    @DisplayName("관리 경로 규칙이 공개 규칙보다 먼저다")
    void adminRuleComesFirst() throws IOException {
        String security = Files.readString(
                MAIN.resolve("com/babplanner/backend/common/security/SecurityConfig.java"));

        // 뒤에 두면 나중에 공개 규칙이 늘어나다 admin까지 걸릴 수 있다.
        assertThat(security.indexOf("\"/api/v1/admin/**\""))
                .isLessThan(security.indexOf("permitAll"));
    }

    @Test
    @DisplayName("컨트롤러가 직접 권한을 판단하지 않는다")
    void controllersDoNotJudgeAuthority() throws IOException {
        for (Source source : sourcesUnder(ADMIN)) {
            // 규칙이 두 곳에 흩어지면 한 곳을 빠뜨렸을 때 그 endpoint만 열린다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("Role.ADMIN")
                    .doesNotContain("hasRole")
                    .doesNotContain("getAuthorities");
        }
    }

    @Test
    @DisplayName("회원가입이 role을 받지 않는다")
    void signupTakesNoRole() throws IOException {
        String request = Files.readString(
                MAIN.resolve("com/babplanner/backend/auth/dto/SignupRequest.java"));

        assertThat(request).doesNotContain("Role");
        assertThat(request).doesNotContain("role");
    }

    @Test
    @DisplayName("권한을 올려 주는 API가 없다")
    void noPromotionEndpoint() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("/promote")
                    .doesNotContain("promoteToAdmin");
        }
    }

    @Test
    @DisplayName("관리자용 별도 엔티티를 만들지 않았다")
    void adminHasNoOwnEntities() throws IOException {
        for (Source source : sourcesUnder(ADMIN)) {
            // 사본을 두면 관리자가 고친 것이 사용자에게 보이지 않는다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("@Entity")
                    .doesNotContain("@Table");
        }
    }

    @Test
    @DisplayName("설문 결과에 사용자 정보를 담지 않는다")
    void surveyResultCarriesNoUserData() throws IOException {
        String dto = Files.readString(ADMIN.resolve("dto/SurveyResultResponse.java"));
        String service = Files.readString(ADMIN.resolve("service/AdminSurveyService.java"));

        for (String forbidden : List.of("userId", "phone", "studentId", "getUserId")) {
            assertThat(dto).as("dto %s", forbidden).doesNotContain(forbidden);
            assertThat(service).as("service %s", forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("적용된 migration을 고치지 않았다")
    void appliedMigrationsAreUntouched() throws IOException {
        try (Stream<Path> paths = Files.list(MIGRATIONS)) {
            List<String> names = paths.map(path -> path.getFileName().toString()).sorted().toList();

            // V23~V29만 새로 늘었다. 앞의 것은 이미 실제 DB에 적용됐다.
            assertThat(names)
                    .contains(
                            "V23__add_congestion_observation_metadata.sql",
                            "V24__create_demand_predictions.sql",
                            "V25__create_procurement_recommendations.sql",
                            "V26__add_reward_transaction_reference.sql",
                            "V27__add_phone_verification_failed_attempts.sql",
                            "V28__add_waste_submission_review.sql",
                            "V29__remove_dev_sample_data.sql");
            assertThat(names).hasSize(29);
        }
    }

    @Test
    @DisplayName("AI 서비스의 내부 endpoint를 흉내 내지 않는다")
    void aiInternalEndpointsAreNotServedHere() throws IOException {
        for (Source source : sourcesUnder(MAIN)) {
            // /internal/v1/ai/** 는 FastAPI가 제공한다. 백엔드가 같은 경로를
            // 열어 주면 어느 쪽이 응답했는지 알 수 없다.
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("@RequestMapping(\"/internal/v1/ai");
        }
    }

    @Test
    @DisplayName("대시보드가 예시 숫자를 내보내지 않는다")
    void dashboardInventsNoNumbers() throws IOException {
        String dashboard = Files.readString(ADMIN.resolve("service/AdminDashboardService.java"));

        // 528, 370은 OpenAPI 예시이자 Figma 화면에 찍힌 값이다. 실행 결과로
        // 나오면 안 된다.
        assertThat(dashboard).doesNotContain("528").doesNotContain("370");
    }

    @Test
    @DisplayName("집계를 자바 메모리에서 돌리지 않는다")
    void aggregationHappensInTheDatabase() throws IOException {
        String repository = Files.readString(
                MAIN.resolve(
                        "com/babplanner/backend/waste/repository/WasteSubmissionRepository.java"));
        String service = Files.readString(ADMIN.resolve("service/AdminWasteService.java"));

        assertThat(repository).contains("avg(w.wasteRate)").contains("group by");
        // findAll로 다 읽어 자바에서 더하면 데이터가 늘어난 만큼 느려진다.
        assertThat(service).doesNotContain("findAll").doesNotContain("Collectors.groupingBy");
    }

    @Test
    @DisplayName("잔반 집계가 분석된 건만 표본으로 삼는다")
    void wasteAggregationCountsAnalyzedOnly() throws IOException {
        String repository = Files.readString(
                MAIN.resolve(
                        "com/babplanner/backend/waste/repository/WasteSubmissionRepository.java"));

        assertThat(repository).contains("w.wasteRate is not null");
        // 기간은 제출 시각이 아니라 메뉴 제공일로 센다.
        assertThat(repository).contains("m.serviceDate between :from and :to");
    }

    @Test
    @DisplayName("메뉴 삭제가 잔반 기록을 함께 지우지 않는다")
    void menuDeleteDoesNotCascade() throws IOException {
        String service = Files.readString(ADMIN.resolve("service/AdminMenuService.java"));

        assertThat(service).doesNotContain("wasteSubmissionRepository.delete");
        assertThat(service).contains("existsByMenuId");
    }
}

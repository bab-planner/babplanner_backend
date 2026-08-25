package com.babplanner.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 민감 정보가 코드/설정으로 새지 않는지 소스 수준에서 고정한다.
 *
 * <p>"로그에 찍지 않는다", "원문을 저장하지 않는다" 같은 부재 조건은 실행
 * 테스트로 잡기 어려워 소스를 직접 훑는다.
 */
class SecuritySourceGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private record Source(String path, String code) {}

    /** 주석을 제외한 실제 코드만 남긴다. 설명 문구가 오탐을 만들지 않도록. */
    private List<Source> mainSources() throws IOException {
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> kept = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.stripLeading();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    kept.add(line);
                }
                sources.add(new Source(
                        path.toString().replace('\\', '/'), String.join("\n", kept)));
            }
        }
        return sources;
    }

    @Test
    @DisplayName("표준 출력으로 무언가를 찍는 코드가 없다")
    void noConsolePrinting() throws IOException {
        for (Source source : mainSources()) {
            assertThat(source.code())
                    .as("%s", source.path())
                    .doesNotContain("System.out.print")
                    .doesNotContain("System.err.print")
                    .doesNotContain("printStackTrace");
        }
    }

    @Test
    @DisplayName("민감한 값을 로그 인자로 넘기는 코드가 없다")
    void noSensitiveLogging() throws IOException {
        // 로그 호출이 들어 있는 줄에 민감 식별자가 함께 등장하면 실패시킨다.
        List<String> sensitive =
                List.of("password", "passwordHash", "code", "token", "accessToken",
                        "refreshToken", "verificationToken", "Authorization");

        for (Source source : mainSources()) {
            for (String line : source.code().split("\n")) {
                if (!line.contains("log.")) {
                    continue;
                }
                for (String needle : sensitive) {
                    assertThat(line.contains("{}") && line.contains(needle))
                            .as("%s 의 로그가 %s 를 남긴다: %s", source.path(), needle, line.strip())
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("엔티티에 원문 토큰/코드 필드가 없다")
    void entitiesStoreHashesOnly() throws IOException {
        Map<String, List<String>> forbiddenFields = Map.of(
                "src/main/java/com/babplanner/backend/auth/domain/PhoneVerification.java",
                List.of("private String code;", "private String verificationToken;"),
                "src/main/java/com/babplanner/backend/auth/domain/RefreshSession.java",
                List.of("private String token;", "private String refreshToken;"),
                "src/main/java/com/babplanner/backend/user/domain/UserEntity.java",
                List.of("private String password;"));

        for (Source source : mainSources()) {
            List<String> forbidden = forbiddenFields.get(source.path());
            if (forbidden == null) {
                continue;
            }
            for (String field : forbidden) {
                assertThat(source.code()).as("%s", source.path()).doesNotContain(field);
            }
        }
    }

    @Test
    @DisplayName("응답 DTO에 passwordHash가 없다")
    void responseDtosHidePasswordHash() throws IOException {
        for (Source source : mainSources()) {
            if (!source.path().contains("/dto/") && !source.path().contains("/controller/")) {
                continue;
            }
            assertThat(source.code()).as("%s", source.path()).doesNotContain("passwordHash");
        }
    }

    @Test
    @DisplayName("설정 파일에 실제 비밀번호나 키가 들어 있지 않다")
    void configHasNoRealSecrets() throws IOException {
        String applicationYml = Files.readString(RESOURCES.resolve("application.yml"));

        // 모든 민감 값은 환경 변수 placeholder여야 한다.
        assertThat(applicationYml).contains("${DB_URL}");
        assertThat(applicationYml).contains("${DB_USERNAME}");
        assertThat(applicationYml).contains("${DB_PASSWORD}");
        assertThat(applicationYml).contains("${JWT_SIGNING_KEY:}");
    }

    @Test
    @DisplayName("운영 프로필에 JWT 서명 키 기본값이 없다")
    void noDefaultSigningKey() throws IOException {
        String applicationYml = Files.readString(RESOURCES.resolve("application.yml"));

        // ${JWT_SIGNING_KEY:} 는 빈 기본값이라 미설정 시 기동에 실패한다.
        assertThat(applicationYml).doesNotContain("signing-key: change-me");
        assertThat(applicationYml).doesNotContain("signing-key: secret");
        for (String line : applicationYml.split("\n")) {
            if (line.contains("signing-key:")) {
                assertThat(line).contains("${JWT_SIGNING_KEY");
            }
        }
    }

    @Test
    @DisplayName("Flyway가 schema를 관리하고 JPA가 만들지 않는다")
    void schemaIsManagedByFlyway() throws IOException {
        String applicationYml = Files.readString(RESOURCES.resolve("application.yml"));

        assertThat(applicationYml).contains("ddl-auto: validate");
        assertThat(applicationYml).doesNotContain("ddl-auto: create");
        assertThat(applicationYml).doesNotContain("ddl-auto: update");
    }

    @Test
    @DisplayName("SMS 구현이 인증번호를 로그로 남기지 않는다")
    void smsSenderDoesNotLogCode() throws IOException {
        Path noOp =
                MAIN.resolve("com/babplanner/backend/auth/service/NoOpSmsSender.java");
        String code = Files.readString(noOp);

        for (String line : code.split("\n")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            if (line.contains("log.")) {
                assertThat(line).doesNotContain("code").doesNotContain("phone");
            }
        }
    }
}

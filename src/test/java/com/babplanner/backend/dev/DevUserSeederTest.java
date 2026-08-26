package com.babplanner.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로컬 E2E용 사용자 fixture의 안전 조건을 확인한다.
 *
 * <p>테스트 프로필에서는 bean이 등록되지 않으므로(local/dev + enabled 조건)
 * 동작 자체는 seeder를 직접 만들어 검증하고, 프로필/조건 제한과 로깅 금지는
 * 소스로 고정한다.
 */
class DevUserSeederTest extends ApiIntegrationTest {

    @Autowired private PasswordEncoder passwordEncoder;

    private static final String DEV_PHONE = "01099998888";
    private static final String DEV_PASSWORD = "dev-only-password-not-real";

    private DevUserProperties properties() {
        DevUserProperties properties = new DevUserProperties();
        properties.setEnabled(true);
        properties.setPhone(DEV_PHONE);
        properties.setPassword(DEV_PASSWORD);
        properties.setStudentId("20990001");
        properties.setName("개발용");
        return properties;
    }

    private DevUserSeeder seeder(DevUserProperties properties) {
        return new DevUserSeeder(
                properties, userRepository, passwordEncoder, java.time.Clock.systemUTC());
    }

    @Test
    @DisplayName("테스트 프로필에서는 seeder bean이 등록되지 않는다")
    void notRegisteredInTestProfile() {
        // local/dev 프로필이 아니므로 컨텍스트에 존재하지 않아야 한다.
        assertThat(userRepository.existsByPhone(DEV_PHONE)).isFalse();
    }

    @Test
    @DisplayName("실행하면 개발 사용자를 만든다")
    void createsUser() {
        seeder(properties()).run((ApplicationArguments) null);

        UserEntity user = userRepository.findByPhone(DEV_PHONE).orElseThrow();
        assertThat(user.getName()).isEqualTo("개발용");
        assertThat(user.getStudentId()).isEqualTo("20990001");
    }

    @Test
    @DisplayName("비밀번호는 BCrypt로 저장된다")
    void passwordIsHashed() {
        seeder(properties()).run((ApplicationArguments) null);

        UserEntity user = userRepository.findByPhone(DEV_PHONE).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(DEV_PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(DEV_PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("권한은 USER다")
    void roleIsUser() {
        seeder(properties()).run((ApplicationArguments) null);

        assertThat(userRepository.findByPhone(DEV_PHONE).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("다시 실행해도 중복 생성하지 않는다")
    void isIdempotent() {
        DevUserSeeder seeder = seeder(properties());
        seeder.run((ApplicationArguments) null);
        seeder.run((ApplicationArguments) null);

        assertThat(userRepository.findAll().stream()
                        .filter(user -> user.getPhone().equals(DEV_PHONE))
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("기존 사용자의 변경 내용을 덮어쓰지 않는다")
    void doesNotOverwriteExistingUser() {
        seeder(properties()).run((ApplicationArguments) null);
        UserEntity user = userRepository.findByPhone(DEV_PHONE).orElseThrow();
        user.updateProfile("사용자가바꾼이름", null, Instant.now());
        userRepository.saveAndFlush(user);

        seeder(properties()).run((ApplicationArguments) null);

        assertThat(userRepository.findByPhone(DEV_PHONE).orElseThrow().getName())
                .isEqualTo("사용자가바꾼이름");
    }

    @Test
    @DisplayName("필수 값이 없으면 생성 단계에서 실패한다")
    void missingValuesFailFast() {
        DevUserProperties incomplete = properties();
        incomplete.setPassword(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> seeder(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bapplanner.dev-user.password");
    }

    @Test
    @DisplayName("빈 문자열도 미설정으로 본다")
    void blankValuesFailFast() {
        DevUserProperties incomplete = properties();
        incomplete.setPhone("   ");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> seeder(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bapplanner.dev-user.phone");
    }

    @Test
    @DisplayName("local/dev 프로필과 명시적 enable 조건이 걸려 있다")
    void restrictedByProfileAndProperty() throws IOException {
        String source =
                Files.readString(
                        Path.of("src/main/java/com/babplanner/backend/dev/DevUserSeeder.java"));

        assertThat(source).contains("@Profile({\"local\", \"dev\"})");
        assertThat(source).contains("name = \"enabled\", havingValue = \"true\"");
    }

    @Test
    @DisplayName("개인 정보를 로그로 남기지 않는다")
    void doesNotLogCredentials() throws IOException {
        String source =
                Files.readString(
                        Path.of("src/main/java/com/babplanner/backend/dev/DevUserSeeder.java"));

        for (String line : source.split("\n")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            if (line.contains("log.")) {
                assertThat(line).doesNotContain("{}").doesNotContain("getPhone").doesNotContain("getPassword");
            }
        }
    }

    @Test
    @DisplayName("설정에 실제 기본값을 넣지 않는다")
    void noDefaultCredentialsInConfig() throws IOException {
        String applicationYml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYml).contains("enabled: ${DEV_USER_ENABLED:false}");
        for (String key : java.util.List.of("phone", "password", "student-id", "name")) {
            assertThat(applicationYml)
                    .as("dev-user.%s", key)
                    .contains(key + ": ${DEV_USER_" + key.toUpperCase().replace('-', '_') + ":}");
        }
    }
}

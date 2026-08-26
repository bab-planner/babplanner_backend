package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.dev.DevAdminProperties;
import com.babplanner.backend.dev.DevAdminSeeder;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * 로컬 관리자 fixture.
 *
 * <p>확인하려는 것은 하나다. 잘못된 설정이 조용히 넘어가지 않는가.
 */
class DevAdminSeederTest extends ApiIntegrationTest {

    /** 테스트 전용 값. 실제로 쓰는 로컬 계정과 겹치지 않게 둔다. */
    private static final String SEED_PHONE = "01000000001";
    private static final String SEED_PASSWORD = "test-only-not-a-real-password";
    private static final String SEED_STUDENT_ID = "20200099";
    private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    private DevAdminProperties properties(
            String phone, String password, String studentId, String name) {
        DevAdminProperties properties = new DevAdminProperties();
        properties.setEnabled(true);
        properties.setPhone(phone);
        properties.setPassword(password);
        properties.setStudentId(studentId);
        properties.setName(name);
        return properties;
    }

    private DevAdminProperties valid() {
        return properties(SEED_PHONE, SEED_PASSWORD, SEED_STUDENT_ID, "관리자");
    }

    private DevAdminSeeder seeder(DevAdminProperties properties) {
        return new DevAdminSeeder(properties, userRepository, passwordEncoder, clock);
    }

    @Test
    @DisplayName("설정이 갖춰지면 관리자를 만든다")
    void createsAdmin() {
        seeder(valid()).run(NO_ARGS);

        UserEntity created = userRepository.findByPhone(SEED_PHONE).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.getStudentId()).isEqualTo(SEED_STUDENT_ID);
    }

    @Test
    @DisplayName("비밀번호를 BCrypt로 저장한다")
    void storesBcryptPassword() {
        seeder(valid()).run(NO_ARGS);

        String hash = userRepository.findByPhone(SEED_PHONE).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(SEED_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(SEED_PASSWORD, hash)).isTrue();
    }

    @Test
    @DisplayName("만든 관리자로 로그인해 관리 API를 쓸 수 있다")
    void seededAdminCanUseAdminApis() throws Exception {
        seeder(valid()).run(NO_ARGS);

        var login = postJson(
                "/api/v1/auth/login",
                java.util.Map.of("phone", SEED_PHONE, "password", SEED_PASSWORD));
        String token = accessTokenOf(body(login).path("data"));

        assertThat(getJson("/api/v1/admin/notices", token).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("두 번 돌려도 계정이 하나다")
    void runningTwiceCreatesOne() {
        seeder(valid()).run(NO_ARGS);
        seeder(valid()).run(NO_ARGS);

        assertThat(userRepository.findAll().stream()
                        .filter(user -> SEED_PHONE.equals(user.getPhone()))
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("이미 있는 관리자의 프로필을 덮어쓰지 않는다")
    void existingAdminIsLeftAlone() {
        userRepository.save(new UserEntity(
                "원래 이름",
                SEED_PHONE,
                SEED_STUDENT_ID,
                null,
                Role.ADMIN,
                passwordEncoder.encode("OriginalPassword!1"),
                clock.instant()));

        seeder(valid()).run(NO_ARGS);

        UserEntity existing = userRepository.findByPhone(SEED_PHONE).orElseThrow();
        assertThat(existing.getName()).isEqualTo("원래 이름");
        assertThat(passwordEncoder.matches("OriginalPassword!1", existing.getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("같은 번호의 일반 사용자를 관리자로 올리지 않는다")
    void existingUserIsNotPromoted() {
        userRepository.save(new UserEntity(
                "일반 사용자",
                SEED_PHONE,
                SEED_STUDENT_ID,
                null,
                Role.USER,
                passwordEncoder.encode("UserPassword!1"),
                clock.instant()));

        // 설정 한 줄로 권한이 올라가면 그 일이 언제 일어났는지 아무도 모른다.
        assertThatThrownBy(() -> seeder(valid()).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동으로 권한을 올리지 않습니다");
        assertThat(userRepository.findByPhone(SEED_PHONE).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("값이 빠지면 기동에 실패한다")
    void missingValuesFailFast() {
        for (DevAdminProperties broken : List.of(
                properties("", SEED_PASSWORD, SEED_STUDENT_ID, "관리자"),
                properties(SEED_PHONE, "  ", SEED_STUDENT_ID, "관리자"),
                properties(SEED_PHONE, SEED_PASSWORD, "", "관리자"),
                properties(SEED_PHONE, SEED_PASSWORD, SEED_STUDENT_ID, ""))) {
            assertThatThrownBy(() -> seeder(broken))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bapplanner.dev-admin");
        }
    }

    @Test
    @DisplayName("실패 메시지가 값을 흘리지 않는다")
    void failureMessageLeaksNothing() {
        assertThatThrownBy(
                        () -> seeder(properties(SEED_PHONE, "", SEED_STUDENT_ID, "관리자")))
                .hasMessageNotContaining(SEED_PHONE)
                .hasMessageNotContaining(SEED_STUDENT_ID);
    }

    @Test
    @DisplayName("기본은 꺼져 있고 값도 없다")
    void disabledByDefault() {
        DevAdminProperties properties = new DevAdminProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getPhone()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getStudentId()).isNull();
        assertThat(properties.getName()).isNull();
    }

    @Test
    @DisplayName("운영 프로필에서는 bean이 만들어지지 않는다")
    void notRegisteredInProduction() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/babplanner/backend/dev/DevAdminSeeder.java"));

        assertThat(source).contains("@Profile({\"local\", \"dev\"})");
        assertThat(source).contains("prefix = \"bapplanner.dev-admin\"");
        assertThat(source).contains("havingValue = \"true\"");
    }

    @Test
    @DisplayName("설정에 실제 기본값이 없다")
    void configurationHasNoDefaults() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("enabled: ${DEV_ADMIN_ENABLED:false}");
        assertThat(yaml).contains("phone: ${DEV_ADMIN_PHONE:}");
        assertThat(yaml).contains("password: ${DEV_ADMIN_PASSWORD:}");
    }

    @Test
    @DisplayName("어떤 값도 로그로 남기지 않는다")
    void logsNoSecrets() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/babplanner/backend/dev/DevAdminSeeder.java"));

        for (String line : source.split("\n")) {
            if (!line.contains("log.")) {
                continue;
            }
            assertThat(line)
                    .doesNotContain("getPhone")
                    .doesNotContain("getPassword")
                    .doesNotContain("getStudentId")
                    .doesNotContain("getName");
        }
    }
}

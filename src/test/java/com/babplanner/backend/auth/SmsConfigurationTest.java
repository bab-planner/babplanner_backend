package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.service.NoOpSmsSender;
import com.babplanner.backend.auth.service.SmsConfig;
import com.babplanner.backend.auth.service.SmsSender;
import com.babplanner.backend.auth.service.VerificationCodeHasher;
import com.babplanner.backend.common.config.AuthProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SmsSender와 인증번호 해시 bean이 어떤 설정에서 선택되는지 확인한다.
 *
 * <p>전체 애플리케이션 컨텍스트 대신 ApplicationContextRunner로 해당
 * 구성만 띄운다. DB가 필요 없고 실패 케이스도 안전하게 검증할 수 있다.
 */
class SmsConfigurationTest {

    private static final String HMAC_KEY = "test-only-phone-hmac-key-not-a-real-secret-98765";
    private static final String SIGNING_KEY = "test-only-signing-key-not-a-real-secret-0123456789";

    @EnableConfigurationProperties(AuthProperties.class)
    static class PropertiesConfig {}

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class, SmsConfig.class)
                .withPropertyValues(
                        "bapplanner.auth.signing-key=" + SIGNING_KEY,
                        "bapplanner.auth.phone-verification-hmac-key=" + HMAC_KEY);
    }

    @Test
    @DisplayName("noop 모드를 명시하면 NoOpSmsSender가 등록된다")
    void noopModeRegistersNoOpSender() {
        runner().withPropertyValues("bapplanner.sms.mode=noop")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SmsSender.class)).isInstanceOf(NoOpSmsSender.class);
                });
    }

    @Test
    @DisplayName("mode를 지정하지 않으면 기동에 실패한다")
    void missingModeFailsFast() {
        runner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage(
                            "SmsSender가 구성되지 않았습니다. 운영에서는 실제 SMS 어댑터를 등록하고, "
                                    + "로컬/개발에서는 bapplanner.sms.mode=noop 을 명시하세요.");
        });
    }

    @Test
    @DisplayName("mode가 noop이 아니면 NoOp가 선택되지 않는다")
    void nonNoopModeDoesNotRegisterNoOp() {
        runner().withPropertyValues("bapplanner.sms.mode=real")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("HMAC 키가 없으면 기동에 실패한다")
    void missingHmacKeyFailsFast() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class, SmsConfig.class)
                .withPropertyValues(
                        "bapplanner.sms.mode=noop",
                        "bapplanner.auth.signing-key=" + SIGNING_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("phone-verification-hmac-key");
                });
    }

    @Test
    @DisplayName("HMAC 키가 JWT 서명 키와 같으면 기동에 실패한다")
    void reusedSigningKeyFailsFast() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class, SmsConfig.class)
                .withPropertyValues(
                        "bapplanner.sms.mode=noop",
                        "bapplanner.auth.signing-key=" + SIGNING_KEY,
                        "bapplanner.auth.phone-verification-hmac-key=" + SIGNING_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("signing-key와 달라야 합니다");
                });
    }

    @Test
    @DisplayName("정상 설정에서는 VerificationCodeHasher가 등록된다")
    void hasherIsRegistered() {
        runner().withPropertyValues("bapplanner.sms.mode=noop")
                .run(context -> assertThat(context).hasSingleBean(VerificationCodeHasher.class));
    }

    @Test
    @DisplayName("NoOpSmsSender는 component scan 대상이 아니다")
    void noOpIsNotComponentScanned() throws IOException {
        String source =
                Files.readString(
                        Path.of(
                                "src/main/java/com/babplanner/backend/auth/service/NoOpSmsSender.java"));

        assertThat(source).doesNotContain("@Component");
        assertThat(source).doesNotContain("@Service");
    }

    @Test
    @DisplayName("NoOpSmsSender가 번호나 인증번호를 로그로 남기지 않는다")
    void noOpDoesNotLogSensitiveValues() throws IOException {
        String source =
                Files.readString(
                        Path.of(
                                "src/main/java/com/babplanner/backend/auth/service/NoOpSmsSender.java"));

        for (String line : source.split("\n")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (line.contains("log.")) {
                assertThat(line).doesNotContain("code").doesNotContain("phone").doesNotContain("{}");
            }
        }
    }

    @Test
    @DisplayName("운영 설정은 SMS 모드 기본값을 두지 않는다")
    void productionHasNoDefaultSmsMode() throws IOException {
        String applicationYml = Files.readString(Path.of("src/main/resources/application.yml"));

        for (String line : applicationYml.split("\n")) {
            if (line.strip().startsWith("mode:")) {
                // 환경 변수 placeholder만 있어야 하고 noop이 기본이면 안 된다.
                assertThat(line).contains("${SMS_MODE").doesNotContain("noop");
            }
        }
    }

    @Test
    @DisplayName("local/dev 프로필에서만 noop을 켠다")
    void noopEnabledOnlyInLocalAndDevProfiles() throws IOException {
        assertThat(Files.readString(Path.of("src/main/resources/application-local.yml")))
                .contains("mode: noop");
        assertThat(Files.readString(Path.of("src/main/resources/application-dev.yml")))
                .contains("mode: noop");
    }
}

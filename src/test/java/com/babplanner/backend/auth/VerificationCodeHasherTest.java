package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.auth.service.HmacVerificationCodeHasher;
import com.babplanner.backend.auth.service.VerificationCodeHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 6자리 인증번호가 키 있는 HMAC으로 보호되는지 확인한다. */
class VerificationCodeHasherTest {

    private static final String KEY = "test-only-phone-hmac-key-not-a-real-secret-98765";

    private final VerificationCodeHasher hasher = new HmacVerificationCodeHasher(KEY);

    @Test
    @DisplayName("같은 번호와 코드는 일치한다")
    void sameInputMatches() {
        String hash = hasher.hash("01012345678", "123456");

        assertThat(hasher.matches("01012345678", "123456", hash)).isTrue();
    }

    @Test
    @DisplayName("코드가 다르면 일치하지 않는다")
    void wrongCodeDoesNotMatch() {
        String hash = hasher.hash("01012345678", "123456");

        assertThat(hasher.matches("01012345678", "000000", hash)).isFalse();
    }

    @Test
    @DisplayName("같은 코드라도 번호가 다르면 해시가 다르다")
    void differentPhoneProducesDifferentHash() {
        String first = hasher.hash("01012345678", "123456");
        String second = hasher.hash("01098765432", "123456");

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches("01098765432", "123456", first)).isFalse();
    }

    @Test
    @DisplayName("HMAC 결과는 평문 SHA-256과 다르다")
    void hashIsNotPlainSha256() throws Exception {
        String code = "123456";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String plainSha =
                HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));

        assertThat(hasher.hash("01012345678", code)).isNotEqualTo(plainSha);
    }

    @Test
    @DisplayName("키가 다르면 같은 입력도 다른 해시가 나온다")
    void keyChangesHash() {
        VerificationCodeHasher other =
                new HmacVerificationCodeHasher("another-test-only-key-not-a-real-secret-13579");

        assertThat(hasher.hash("01012345678", "123456"))
                .isNotEqualTo(other.hash("01012345678", "123456"));
    }

    @Test
    @DisplayName("저장된 해시가 null이면 일치하지 않는다")
    void nullHashDoesNotMatch() {
        assertThat(hasher.matches("01012345678", "123456", null)).isFalse();
    }

    @Test
    @DisplayName("키가 없으면 생성 단계에서 실패한다")
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> new HmacVerificationCodeHasher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone-verification-hmac-key");

        assertThatThrownBy(() -> new HmacVerificationCodeHasher("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 짧으면 생성 단계에서 실패한다")
    void shortKeyFailsFast() {
        assertThatThrownBy(() -> new HmacVerificationCodeHasher("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("운영 소스에 실제 HMAC 키가 들어 있지 않다")
    void noHardcodedKeyInSources() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main"))) {
            List<Path> files =
                    paths.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".yml"))
                            .toList();
            for (Path path : files) {
                String content = Files.readString(path);
                assertThat(content).as("%s", path).doesNotContain(KEY);
                if (!path.toString().endsWith(".yml")) {
                    // Java 소스에는 오류 메시지로 설정 키 이름이 등장할 수 있다.
                    continue;
                }
                for (String line : content.split("\n")) {
                    if (line.strip().startsWith("phone-verification-hmac-key:")) {
                        // 설정에는 환경 변수 placeholder만 있어야 한다.
                        assertThat(line).contains("${PHONE_VERIFICATION_HMAC_KEY");
                    }
                }
            }
        }
    }
}

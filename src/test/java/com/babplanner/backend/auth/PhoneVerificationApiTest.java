package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.domain.PhoneVerification;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class PhoneVerificationApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("send-code가 성공 envelope와 TTL을 돌려준다")
    void sendCodeSucceeds() throws Exception {
        MvcResult result = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        assertThat(json.path("data").path("expiresInSeconds").asInt()).isEqualTo(180);
        assertThat(json.path("data").path("resendAvailableInSeconds").asInt()).isEqualTo(60);
    }

    @Test
    @DisplayName("send-code 응답에 인증번호 자체는 들어가지 않는다")
    void sendCodeDoesNotLeakCode() throws Exception {
        MvcResult result = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        String code = smsSender.lastCodeFor(PHONE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(code);
    }

    @Test
    @DisplayName("형식에 맞지 않는 번호는 400 검증 오류다")
    void sendCodeRejectsInvalidPhone() throws Exception {
        MvcResult result = postJson("/api/v1/auth/phone/send-code", Map.of("phone", "0212345678"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("재전송 쿨다운 안에서는 429로 막는다")
    void sendCodeRespectsCooldown() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        MvcResult second = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        assertThat(second.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(second).path("error").path("code").asText()).isEqualTo("RESEND_TOO_SOON");
        assertThat(smsSender.sentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿨다운이 지나면 다시 보낼 수 있다")
    void sendCodeAfterCooldown() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        clock.advance(Duration.ofSeconds(61));
        MvcResult second = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(smsSender.sentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("인증번호 원문은 DB에 저장되지 않는다")
    void codeIsStoredHashedOnly() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        String code = smsSender.lastCodeFor(PHONE);
        PhoneVerification saved = phoneVerificationRepository.findAll().getFirst();
        assertThat(saved.getCodeHash()).isNotEqualTo(code);
        assertThat(saved.getCodeHash()).hasSize(64);
    }

    @Test
    @DisplayName("올바른 인증번호로 verificationToken을 받는다")
    void verifySucceeds() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String code = smsSender.lastCodeFor(PHONE);

        MvcResult result = postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", code));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("verificationToken").asText()).isNotBlank();
        assertThat(data.path("expiresInSeconds").asInt()).isEqualTo(600);
    }

    @Test
    @DisplayName("틀린 인증번호는 400이다")
    void verifyRejectsWrongCode() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        MvcResult result = postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", "000000"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_CODE");
    }

    @Test
    @DisplayName("만료된 인증번호는 400이다")
    void verifyRejectsExpiredCode() throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        String code = smsSender.lastCodeFor(PHONE);

        clock.advance(Duration.ofMinutes(4));
        MvcResult result = postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", code));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_CODE");
    }

    @Test
    @DisplayName("verificationToken 원문은 DB에 저장되지 않는다")
    void verificationTokenStoredHashedOnly() throws Exception {
        String token = verifiedToken(PHONE);

        PhoneVerification saved = phoneVerificationRepository.findAll().getFirst();
        assertThat(saved.getVerificationTokenHash()).isNotEqualTo(token);
        assertThat(saved.getVerificationTokenHash()).hasSize(64);
    }
}

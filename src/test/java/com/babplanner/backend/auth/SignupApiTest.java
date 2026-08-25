package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

class SignupApiTest extends ApiIntegrationTest {

    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("정상 회원가입은 201과 AuthEnvelope를 돌려준다")
    void signupSucceeds() throws Exception {
        String token = verifiedToken(PHONE);

        MvcResult result = postJson("/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, token));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        JsonNode data = json.path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("user").path("phone").asText()).isEqualTo(PHONE);
        assertThat(data.path("user").path("studentId").asText()).isEqualTo(STUDENT_ID);
    }

    @Test
    @DisplayName("가입한 사용자 role은 USER다")
    void signupAssignsUserRole() throws Exception {
        JsonNode data = signup();

        assertThat(data.path("user").path("role").asText()).isEqualTo("USER");
        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("응답 어디에도 passwordHash가 없다")
    void signupResponseHidesPasswordHash() throws Exception {
        String token = verifiedToken(PHONE);

        MvcResult result = postJson("/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, token));

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("passwordHash");
        assertThat(content).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("비밀번호는 BCrypt 해시로만 저장된다")
    void passwordIsStoredHashed() throws Exception {
        signup();

        UserEntity user = userRepository.findByPhone(PHONE).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("인증하지 않은 번호는 가입할 수 없다")
    void signupRejectsUnverifiedPhone() throws Exception {
        MvcResult result = postJson(
                "/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, "not-a-real-token"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_TOKEN");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("토큰이 인증한 번호와 가입 번호가 다르면 거부한다")
    void signupRejectsPhoneMismatch() throws Exception {
        String token = verifiedToken(PHONE);

        MvcResult result = postJson(
                "/api/v1/auth/signup", signupPayload("01098765432", STUDENT_ID, token));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_TOKEN");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 verificationToken을 두 번 쓸 수 없다")
    void verificationTokenIsSingleUse() throws Exception {
        String token = verifiedToken(PHONE);
        postJson("/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, token));

        MvcResult second =
                postJson("/api/v1/auth/signup", signupPayload("01098765432", "20205678", token));

        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(second).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_TOKEN");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 verificationToken은 거부한다")
    void expiredVerificationTokenIsRejected() throws Exception {
        String token = verifiedToken(PHONE);

        clock.advance(Duration.ofMinutes(11));
        MvcResult result = postJson("/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, token));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_VERIFICATION_TOKEN");
    }

    @Test
    @DisplayName("서비스 이용약관에 동의하지 않으면 거부한다")
    void signupRequiresServiceTerms() throws Exception {
        String token = verifiedToken(PHONE);
        Map<String, Object> payload = new HashMap<>(signupPayload(PHONE, STUDENT_ID, token));
        payload.put("agreements", Map.of("serviceTerms", false, "privacyPolicy", true));

        MvcResult result = postJson("/api/v1/auth/signup", payload);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("TERMS_NOT_AGREED");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("개인정보처리방침에 동의하지 않으면 거부한다")
    void signupRequiresPrivacyPolicy() throws Exception {
        String token = verifiedToken(PHONE);
        Map<String, Object> payload = new HashMap<>(signupPayload(PHONE, STUDENT_ID, token));
        payload.put("agreements", Map.of("serviceTerms", true, "privacyPolicy", false));

        MvcResult result = postJson("/api/v1/auth/signup", payload);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("TERMS_NOT_AGREED");
    }

    @Test
    @DisplayName("이미 가입된 휴대폰 번호는 409다")
    void duplicatePhoneConflicts() throws Exception {
        signup();

        clock.advance(Duration.ofMinutes(2));
        String token = verifiedToken(PHONE);
        MvcResult result = postJson("/api/v1/auth/signup", signupPayload(PHONE, "20209999", token));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("DUPLICATE_PHONE");
    }

    @Test
    @DisplayName("이미 가입된 학번은 409다")
    void duplicateStudentIdConflicts() throws Exception {
        signup();

        String token = verifiedToken("01098765432");
        MvcResult result =
                postJson("/api/v1/auth/signup", signupPayload("01098765432", STUDENT_ID, token));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("DUPLICATE_STUDENT_ID");
    }

    @Test
    @DisplayName("gender는 생략할 수 있다")
    void genderIsOptional() throws Exception {
        JsonNode data = signup();

        assertThat(data.path("user").has("gender")).isTrue();
        assertThat(data.path("user").path("gender").isNull()).isTrue();
    }

    @Test
    @DisplayName("gender를 지정하면 그대로 반영된다")
    void genderIsStoredWhenProvided() throws Exception {
        String token = verifiedToken(PHONE);
        Map<String, Object> payload = new HashMap<>(signupPayload(PHONE, STUDENT_ID, token));
        payload.put("gender", "MALE");

        MvcResult result = postJson("/api/v1/auth/signup", payload);

        assertThat(body(result).path("data").path("user").path("gender").asText()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("짧은 비밀번호는 400 검증 오류다")
    void shortPasswordRejected() throws Exception {
        String token = verifiedToken(PHONE);
        Map<String, Object> payload = new HashMap<>(signupPayload(PHONE, STUDENT_ID, token));
        payload.put("password", "short");

        MvcResult result = postJson("/api/v1/auth/signup", payload);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
}

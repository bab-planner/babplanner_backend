package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 경로별 접근 정책이 OpenAPI security와 일치하는지 확인한다. */
class SecurityPolicyTest extends ApiIntegrationTest {

    @Test
    @DisplayName("토큰 없이 /users/me에 접근하면 401이다")
    void meRequiresToken() throws Exception {
        MvcResult result = getJson("/api/v1/users/me");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("error").path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("유효한 access token이면 /users/me에 접근할 수 있다")
    void validTokenGrantsAccess() throws Exception {
        JsonNode data = signup();

        MvcResult result = getJson("/api/v1/users/me", accessTokenOf(data));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("형식이 깨진 JWT는 401이다")
    void malformedTokenRejected() throws Exception {
        MvcResult result = getJson("/api/v1/users/me", "not.a.jwt");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("서명이 다른 JWT는 401이다")
    void tamperedTokenRejected() throws Exception {
        JsonNode data = signup();
        String token = accessTokenOf(data);
        // 서명 부분만 바꿔서 위조를 흉내 낸다.
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAA";

        MvcResult result = getJson("/api/v1/users/me", tampered);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("만료된 access token은 401이다")
    void expiredTokenRejected() throws Exception {
        JsonNode data = signup();

        clock.advance(Duration.ofMinutes(31));
        MvcResult result = getJson("/api/v1/users/me", accessTokenOf(data));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("/allergies는 토큰 없이 접근할 수 있다")
    void allergyCatalogIsPublic() throws Exception {
        MvcResult result = getJson("/api/v1/allergies");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("인증 관련 공개 endpoint는 토큰 없이 접근할 수 있다")
    void authEndpointsArePublic() throws Exception {
        // 인증 실패(401)가 아니라 각자의 도메인 응답이 나와야 공개 endpoint다.
        assertThat(postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", "000000"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        assertThat(postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        assertThat(postJson("/api/v1/auth/refresh", Map.of("refreshToken", "test-token"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("/users/me/allergies도 인증이 필요하다")
    void myAllergiesRequireToken() throws Exception {
        assertThat(getJson("/api/v1/users/me/allergies").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("인증 실패 응답도 공통 ErrorEnvelope 모양이다")
    void unauthorizedUsesCommonEnvelope() throws Exception {
        JsonNode json = body(getJson("/api/v1/users/me"));

        assertThat(json.has("success")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("error")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("error").path("message").asText()).isNotBlank();
    }
}

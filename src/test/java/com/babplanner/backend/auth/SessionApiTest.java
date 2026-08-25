package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.auth.domain.RefreshSession;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 로그인 / refresh 회전 / 로그아웃. */
class SessionApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("정상 로그인은 AuthEnvelope를 돌려준다")
    void loginSucceeds() throws Exception {
        signup();

        MvcResult result =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        JsonNode data = json.path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("user").path("id").asLong()).isPositive();
    }

    @Test
    @DisplayName("로그인 응답에 passwordHash가 없다")
    void loginResponseHidesPasswordHash() throws Exception {
        signup();

        MvcResult result =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD));

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("passwordHash");
        assertThat(content).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401이다")
    void loginRejectsWrongPassword() throws Exception {
        signup();

        MvcResult result =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", "wrong-password"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("없는 번호와 틀린 비밀번호가 같은 응답을 준다")
    void loginDoesNotRevealAccountExistence() throws Exception {
        signup();

        MvcResult unknownPhone =
                postJson("/api/v1/auth/login", Map.of("phone", "01055556666", "password", PASSWORD));
        MvcResult wrongPassword =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", "wrong-password"));

        assertThat(unknownPhone.getResponse().getStatus())
                .isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(body(unknownPhone).path("error").path("code").asText())
                .isEqualTo(body(wrongPassword).path("error").path("code").asText());
        assertThat(body(unknownPhone).path("error").path("message").asText())
                .isEqualTo(body(wrongPassword).path("error").path("message").asText());
    }

    @Test
    @DisplayName("refresh token 원문은 DB에 저장되지 않는다")
    void refreshTokenStoredHashedOnly() throws Exception {
        JsonNode data = signup();
        String refreshToken = data.path("refreshToken").asText();

        RefreshSession session = refreshSessionRepository.findAll().getFirst();
        assertThat(session.getTokenHash()).isNotEqualTo(refreshToken);
        assertThat(session.getTokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("refresh로 새 access token을 받는다")
    void refreshIssuesNewAccessToken() throws Exception {
        JsonNode data = signup();
        String oldAccess = data.path("accessToken").asText();

        // JWT는 초 단위 만료를 담으므로 시간을 조금 밀어 다른 토큰이 나오게 한다.
        clock.advance(Duration.ofSeconds(5));
        MvcResult result = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", data.path("refreshToken").asText()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String newAccess = body(result).path("data").path("accessToken").asText();
        assertThat(newAccess).isNotBlank().isNotEqualTo(oldAccess);
    }

    @Test
    @DisplayName("refresh는 refresh token도 회전시킨다")
    void refreshRotatesRefreshToken() throws Exception {
        JsonNode data = signup();
        String oldRefresh = data.path("refreshToken").asText();

        MvcResult result = postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));

        String newRefresh = body(result).path("data").path("refreshToken").asText();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);
        assertThat(refreshSessionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 번 쓴 refresh token은 재사용할 수 없다")
    void usedRefreshTokenCannotBeReused() throws Exception {
        JsonNode data = signup();
        String oldRefresh = data.path("refreshToken").asText();
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));

        MvcResult reuse = postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));

        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(reuse).path("error").path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("만료된 refresh token은 401이다")
    void expiredRefreshTokenRejected() throws Exception {
        JsonNode data = signup();

        clock.advance(Duration.ofDays(15));
        MvcResult result = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", data.path("refreshToken").asText()));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("존재하지 않는 refresh token은 401이다")
    void unknownRefreshTokenRejected() throws Exception {
        signup();

        MvcResult result =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", "test-not-a-real-refresh"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("refresh는 Authorization 헤더 없이 동작한다")
    void refreshDoesNotRequireBearer() throws Exception {
        JsonNode data = signup();

        MvcResult result = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", data.path("refreshToken").asText()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("logout은 204를 돌려주고 해당 세션을 revoke한다")
    void logoutRevokesSession() throws Exception {
        JsonNode data = signup();
        String refreshToken = data.path("refreshToken").asText();

        MvcResult result = postJson(
                "/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessTokenOf(data));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(refreshSessionRepository.findAll().getFirst().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("logout 후에는 그 refresh token을 쓸 수 없다")
    void refreshFailsAfterLogout() throws Exception {
        JsonNode data = signup();
        String refreshToken = data.path("refreshToken").asText();
        postJson("/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessTokenOf(data));

        MvcResult result = postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("이미 revoke된 토큰으로 다시 logout해도 성공 처리한다")
    void logoutIsIdempotent() throws Exception {
        JsonNode data = signup();
        String refreshToken = data.path("refreshToken").asText();
        postJson("/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessTokenOf(data));

        MvcResult second = postJson(
                "/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessTokenOf(data));

        assertThat(second.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("refreshToken 없는 logout도 성공한다")
    void logoutWithoutRefreshTokenSucceeds() throws Exception {
        JsonNode data = signup();

        MvcResult result = perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenOf(data)));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("logout은 인증이 필요하다")
    void logoutRequiresAuthentication() throws Exception {
        JsonNode data = signup();

        MvcResult result = postJson(
                "/api/v1/auth/logout", Map.of("refreshToken", data.path("refreshToken").asText()));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("다른 사용자의 refresh token은 revoke하지 못한다")
    void logoutCannotRevokeOtherUsersSession() throws Exception {
        JsonNode first = signup();
        JsonNode second = signup("01098765432", "20205678");

        postJson(
                "/api/v1/auth/logout",
                Map.of("refreshToken", second.path("refreshToken").asText()),
                accessTokenOf(first));

        MvcResult refresh = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", second.path("refreshToken").asText()));
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
    }
}

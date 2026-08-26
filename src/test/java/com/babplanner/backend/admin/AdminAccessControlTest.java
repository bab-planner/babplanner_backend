package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리 API 접근 권한.
 *
 * <p>익명은 401, 로그인한 일반 사용자는 403이다. 둘을 같은 응답으로 묶으면
 * 로그인이 필요한 것인지 권한이 없는 것인지 알 수 없다.
 */
class AdminAccessControlTest extends ApiIntegrationTest {

    private static final List<String> ADMIN_PATHS = List.of(
            "/api/v1/admin/menus?date=2026-08-26",
            "/api/v1/admin/notices",
            "/api/v1/admin/surveys",
            "/api/v1/admin/inquiries");

    @Test
    @DisplayName("익명 접근은 401이다")
    void anonymousIsUnauthorized() throws Exception {
        for (String path : ADMIN_PATHS) {
            assertThat(getJson(path).getResponse().getStatus()).as("%s", path).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void regularUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        for (String path : ADMIN_PATHS) {
            assertThat(getJson(path, token).getResponse().getStatus())
                    .as("%s", path)
                    .isEqualTo(403);
        }
    }

    @Test
    @DisplayName("관리자는 통과한다")
    void adminIsAllowed() throws Exception {
        String token = adminToken();

        for (String path : ADMIN_PATHS) {
            assertThat(getJson(path, token).getResponse().getStatus())
                    .as("%s", path)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("쓰기 경로도 일반 사용자에게 막힌다")
    void writeEndpointsAreForbiddenForUsers() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(postJson("/api/v1/admin/notices", Map.of("title", "t", "content", "c"), token)
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(putJson("/api/v1/admin/notices/1", Map.of("title", "t", "content", "c"), token)
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/admin/notices/1")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("회원가입으로 만든 계정은 일반 사용자다")
    void signupCreatesRegularUser() throws Exception {
        signup();

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("회원가입 본문에 role을 넣어도 관리자가 되지 않는다")
    void signupIgnoresRoleInBody() throws Exception {
        String token = verifiedToken(PHONE);
        Map<String, Object> payload = new java.util.HashMap<>(
                signupPayload(PHONE, STUDENT_ID, token));
        payload.put("role", "ADMIN");

        postJson("/api/v1/auth/signup", payload);

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("가짜 역할 헤더는 무시된다")
    void fakeRoleHeadersAreIgnored() throws Exception {
        String token = accessTokenOf(signup());

        MvcResult result = perform(get("/api/v1/admin/notices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Role", "ADMIN")
                .header("X-Admin", "true"));

        // 권한의 출처는 검증된 토큰뿐이다.
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("헤더만으로는 익명이 관리자가 되지 않는다")
    void headersAloneGrantNothing() throws Exception {
        MvcResult result = perform(get("/api/v1/admin/notices").header("X-Role", "ADMIN"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("관리자도 일반 사용자 API를 그대로 쓴다")
    void adminCanStillUseUserApis() throws Exception {
        String token = adminToken();

        assertThat(getJson("/api/v1/users/me", token).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/inquiries", token).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/rewards/summary", token).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("관리자 응답도 같은 봉투를 쓴다")
    void adminUsesTheSameEnvelope() throws Exception {
        JsonNode json = body(getJson("/api/v1/admin/notices", adminToken()));

        assertThat(json.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("success", "data", "error");
    }

    @Test
    @DisplayName("권한 없음 응답이 내부 사정을 흘리지 않는다")
    void forbiddenResponseSaysNothingExtra() throws Exception {
        MvcResult result = getJson("/api/v1/admin/notices", accessTokenOf(signup()));

        assertThat(body(result).path("error").path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception")
                .doesNotContain("select ");
    }
}

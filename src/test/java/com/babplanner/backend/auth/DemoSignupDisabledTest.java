package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "bapplanner.demo.disable-signup=true")
class DemoSignupDisabledTest extends ApiIntegrationTest {

    @Test
    @DisplayName("공개 시연 모드에서 휴대폰 인증/회원가입은 공통 에러 형식의 403이다")
    void signupPathsBlocked() throws Exception {
        for (String path : new String[] {
            "/api/v1/auth/phone/send-code", "/api/v1/auth/phone/verify", "/api/v1/auth/signup"}) {
            MvcResult r = postJson(path, Map.of("phone", PHONE));
            assertThat(r.getResponse().getStatus()).as(path).isEqualTo(403);
            assertThat(body(r).path("success").asBoolean(true)).isFalse();
            assertThat(body(r).path("error").path("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("공개 시연 모드에서도 관리자 로그인/refresh/관리자 API는 동작한다")
    void adminStillWorks() throws Exception {
        var auth = adminAuth();
        assertThat(accessTokenOf(auth)).isNotBlank();
        assertThat(getJson("/api/v1/admin/menus?date=2026-08-26", accessTokenOf(auth))
                        .getResponse().getStatus())
                .isEqualTo(200);

        MvcResult refreshed = postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", auth.path("refreshToken").asText()));
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/admin/menus?date=2026-08-26").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("로그인 경로는 시연 모드의 영향을 받지 않는다")
    void loginUntouched() throws Exception {
        MvcResult r = postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }
}

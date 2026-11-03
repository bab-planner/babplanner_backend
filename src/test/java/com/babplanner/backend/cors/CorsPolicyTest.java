package com.babplanner.backend.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.babplanner.backend.common.config.CorsProperties;
import com.babplanner.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "bapplanner.cors.allowed-origins=https://admin.example.app, https://second.example.app:8443"
})
class CorsPolicyTest extends ApiIntegrationTest {

    private static final String ALLOWED = "https://admin.example.app";
    private static final String DENIED = "https://evil.example.com";

    private MockHttpServletResponse preflight(String path, String origin, String method)
            throws Exception {
        return perform(options(path)
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,content-type"))
                .getResponse();
    }

    @Test
    @DisplayName("허용 Origin의 preflight는 인증 없이 성공하고 Authorization 헤더를 허용한다")
    void allowedPreflight() throws Exception {
        for (String path : new String[] {"/api/v1/admin/menus", "/api/v1/auth/login"}) {
            MockHttpServletResponse res = preflight(path, ALLOWED, "PATCH");
            assertThat(res.getStatus()).as(path).isEqualTo(200);
            assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);
            assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                    .containsIgnoringCase("Authorization")
                    .containsIgnoringCase("Content-Type");
            assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PATCH");
            assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        }
        assertThat(preflight("/api/v1/auth/login", "https://second.example.app:8443", "POST")
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("미허용 Origin은 CORS 헤더를 받지 못한다")
    void deniedOrigin() throws Exception {
        MockHttpServletResponse pre = preflight("/api/v1/auth/login", DENIED, "POST");
        assertThat(pre.getStatus()).isEqualTo(403);
        assertThat(pre.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();

        MockHttpServletResponse res = perform(get("/api/v1/allergies")
                        .header(HttpHeaders.ORIGIN, DENIED))
                .getResponse();
        assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();

        // http, 다른 포트, 접미사 위장 Origin도 정확 일치가 아니므로 거절된다.
        for (String origin : new String[] {
            "http://admin.example.app", "https://admin.example.app:444",
            "https://admin.example.app.evil.com", "null"}) {
            assertThat(preflight("/api/v1/auth/login", origin, "POST")
                            .getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .as(origin)
                    .isNull();
        }
    }

    @Test
    @DisplayName("허용 Origin의 실제 요청에는 CORS 헤더가 붙고 인증 정책은 그대로다")
    void actualRequestKeepsAuthPolicy() throws Exception {
        MockHttpServletResponse ok = perform(get("/api/v1/allergies")
                        .header(HttpHeaders.ORIGIN, ALLOWED))
                .getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);

        MockHttpServletResponse anon = perform(get("/api/v1/admin/menus?date=2026-08-26")
                        .header(HttpHeaders.ORIGIN, ALLOWED))
                .getResponse();
        assertThat(anon.getStatus()).isEqualTo(401);
        assertThat(anon.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);

        String userToken = accessTokenOf(signup());
        assertThat(perform(get("/api/v1/admin/menus?date=2026-08-26")
                                .header(HttpHeaders.ORIGIN, ALLOWED)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                        .getResponse().getStatus())
                .isEqualTo(403);

        assertThat(perform(get("/api/v1/admin/menus?date=2026-08-26")
                                .header(HttpHeaders.ORIGIN, ALLOWED)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                        .getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Origin이 없는 서버 간 요청은 그대로 동작한다")
    void noOriginRequest() throws Exception {
        MockHttpServletResponse res = getJson("/api/v1/allergies").getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("/internal/v1/** 은 브라우저 CORS를 허용하지 않는다")
    void internalNotCors() throws Exception {
        MockHttpServletResponse pre =
                preflight("/internal/v1/congestion-observations", ALLOWED, "POST");
        assertThat(pre.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        MockHttpServletResponse res = perform(get("/internal/v1/congestion-observations")
                        .header(HttpHeaders.ORIGIN, ALLOWED))
                .getResponse();
        assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("Origin 설정은 정확한 https만 받고 기본은 빈 목록이다")
    void originParsing() {
        assertThat(new CorsProperties().parsedOrigins()).isEmpty();
        CorsProperties p = new CorsProperties();
        p.setAllowedOrigins(" https://A.example.app ,,https://a.example.app");
        assertThat(p.parsedOrigins()).containsExactly("https://a.example.app");
        for (String bad : new String[] {
            "*", "https://*.example.app", "http://a.example.app", "https://a.example.app/",
            "https://a.example.app/path", "a.example.app"}) {
            CorsProperties q = new CorsProperties();
            q.setAllowedOrigins(bad);
            assertThatThrownBy(q::parsedOrigins).as(bad).isInstanceOf(IllegalStateException.class);
        }
    }
}

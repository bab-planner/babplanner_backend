package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공개 조회 endpoint가 인증 때문에 막히지 않는지 확인한다.
 *
 * <p>Menu/Congestion/Notice Controller는 아직 없으므로 401이 아니라 404가
 * 나와야 한다. 401이 나오면 security 정책이 OpenAPI와 어긋난 것이다.
 */
class PublicEndpointMatcherTest extends ApiIntegrationTest {

    private void assertNotBlockedByAuth(String path) throws Exception {
        MvcResult result = getJson(path);

        assertThat(result.getResponse().getStatus())
                .as("%s 는 공개 endpoint라 인증으로 막히면 안 된다", path)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("GET /allergies는 익명으로 200이다")
    void allergiesIsPublic() throws Exception {
        assertThat(getJson("/api/v1/allergies").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /menus는 인증이 아니라 미구현으로 404다")
    void menusListIsPublic() throws Exception {
        assertNotBlockedByAuth("/api/v1/menus");
        assertThat(getJson("/api/v1/menus").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /menus/{id}도 인증으로 막히지 않는다")
    void menuDetailIsPublic() throws Exception {
        assertNotBlockedByAuth("/api/v1/menus/1");
        assertThat(getJson("/api/v1/menus/1").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /congestion/current도 인증으로 막히지 않는다")
    void congestionIsPublic() throws Exception {
        assertNotBlockedByAuth("/api/v1/congestion/current");
        assertThat(getJson("/api/v1/congestion/current").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /notices도 인증으로 막히지 않는다")
    void noticesListIsPublic() throws Exception {
        assertNotBlockedByAuth("/api/v1/notices");
        assertThat(getJson("/api/v1/notices").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /notices/{id}도 인증으로 막히지 않는다")
    void noticeDetailIsPublic() throws Exception {
        assertNotBlockedByAuth("/api/v1/notices/1");
        assertThat(getJson("/api/v1/notices/1").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("메뉴 쓰기(POST)는 공개되지 않는다")
    void menuWriteIsNotPublic() throws Exception {
        MvcResult result = postJson("/api/v1/menus", java.util.Map.of());

        // GET만 permitAll이므로 POST는 인증에 걸려야 한다.
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("/users/me는 익명 접근 시 401이다")
    void userMeRequiresAuth() throws Exception {
        assertThat(getJson("/api/v1/users/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("/rewards/summary는 익명 접근 시 401이다")
    void rewardsRequireAuth() throws Exception {
        assertThat(getJson("/api/v1/rewards/summary").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("설문/잔반/문의도 익명 접근 시 401이다")
    void otherPrivateApisRequireAuth() throws Exception {
        assertThat(getJson("/api/v1/surveys/active").getResponse().getStatus()).isEqualTo(401);
        assertThat(getJson("/api/v1/waste-submissions/1").getResponse().getStatus()).isEqualTo(401);
        assertThat(getJson("/api/v1/inquiries").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("관리자 API는 익명 접근 시 401이다")
    void adminApisRequireAuth() throws Exception {
        assertThat(getJson("/api/v1/admin/dashboard").getResponse().getStatus()).isEqualTo(401);
        assertThat(getJson("/api/v1/admin/menus").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("SecurityConfig에 admin 경로를 permitAll로 두지 않는다")
    void adminIsNeverPermitted() throws IOException {
        String source =
                Files.readString(
                        Path.of(
                                "src/main/java/com/babplanner/backend/common/security/SecurityConfig.java"));

        assertThat(source).doesNotContain("/api/v1/admin");
        // 전체 경로를 통째로 여는 matcher도 없어야 한다.
        assertThat(source).doesNotContain("\"/api/v1/**\"");
        assertThat(source).doesNotContain("anyRequest().permitAll()");
    }
}

package com.babplanner.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 앱이 실제로 밟는 순서대로 전 구간을 한 번에 통과시킨다.
 *
 * <p>send-code → verify → signup → login → refresh → me → update → allergies →
 * logout. 외부 네트워크 호출은 없다.
 */
class ApiSmokeTest extends ApiIntegrationTest {

    @Test
    @DisplayName("가입부터 로그아웃까지 전체 흐름이 통과한다")
    void fullFlow() throws Exception {
        // 1. 인증번호 발송
        MvcResult sendCode = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));
        assertThat(sendCode.getResponse().getStatus()).isEqualTo(200);

        // 2. 인증번호 검증
        String code = smsSender.lastCodeFor(PHONE);
        MvcResult verify =
                postJson("/api/v1/auth/phone/verify", Map.of("phone", PHONE, "code", code));
        String verificationToken = body(verify).path("data").path("verificationToken").asText();
        assertThat(verificationToken).isNotBlank();

        // 3. 회원가입
        MvcResult signup = postJson(
                "/api/v1/auth/signup", signupPayload(PHONE, STUDENT_ID, verificationToken));
        assertThat(signup.getResponse().getStatus()).isEqualTo(201);

        // 4. 로그인
        MvcResult login =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        JsonNode session = body(login).path("data");
        String accessToken = session.path("accessToken").asText();
        String refreshToken = session.path("refreshToken").asText();

        // 5. 토큰 재발급
        MvcResult refresh =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        JsonNode rotated = body(refresh).path("data");
        accessToken = rotated.path("accessToken").asText();
        refreshToken = rotated.path("refreshToken").asText();

        // 6. 내 정보 조회
        MvcResult me = getJson("/api/v1/users/me", accessToken);
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(me).path("data").path("phone").asText()).isEqualTo(PHONE);

        // 7. 내 정보 수정
        MvcResult update = patchJson(
                "/api/v1/users/me", Map.of("name", "김철수", "gender", "FEMALE"), accessToken);
        assertThat(body(update).path("data").path("name").asText()).isEqualTo("김철수");

        // 8. 알레르기 master 조회 (공개)
        MvcResult catalog = getJson("/api/v1/allergies");
        assertThat(catalog.getResponse().getStatus()).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        body(catalog).path("data").forEach(node -> ids.add(node.path("id").asLong()));
        assertThat(ids).hasSize(19);

        // 9. 내 알레르기 저장
        MvcResult saveAllergies = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(ids.get(0), ids.get(4))),
                accessToken);
        assertThat(saveAllergies.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(saveAllergies).path("data")).hasSize(2);

        // 10. 내 알레르기 조회
        MvcResult myAllergies = getJson("/api/v1/users/me/allergies", accessToken);
        assertThat(body(myAllergies).path("data")).hasSize(2);

        // 11. 로그아웃
        MvcResult logout =
                postJson("/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessToken);
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);

        // 로그아웃 후에는 그 refresh token을 다시 쓸 수 없다.
        assertThat(postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("성공 응답은 success/data/error 세 키를 모두 갖는다")
    void successEnvelopeShape() throws Exception {
        JsonNode json = body(getJson("/api/v1/allergies"));

        assertThat(json.has("success")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("error")).isTrue();
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
    }

    @Test
    @DisplayName("login 응답 JSON 모양이 Flutter 파서 기대와 일치한다")
    void loginEnvelopeMatchesFlutterContract() throws Exception {
        signup();

        MvcResult result =
                postJson("/api/v1/auth/login", Map.of("phone", PHONE, "password", PASSWORD));

        JsonNode json = body(result);
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("success", "data", "error");
        JsonNode data = json.path("data");
        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("accessToken", "refreshToken", "user");
        assertThat(data.path("user").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "name", "phone", "studentId", "gender", "role");
    }

    @Test
    @DisplayName("검증 오류 응답은 어떤 필드가 문제인지만 알려준다")
    void validationErrorEnvelope() throws Exception {
        MvcResult result = postJson("/api/v1/auth/phone/send-code", Map.of("phone", "abc"));

        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.path("error").path("details").path("fields").has("phone")).isTrue();
        // 입력 값 자체는 담지 않는다.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"abc\"");
    }

    @Test
    @DisplayName("예상치 못한 오류에도 내부 예외가 노출되지 않는다")
    void unexpectedErrorHidesInternals() throws Exception {
        // 존재하지 않는 경로는 인증 정책상 401이 되고, 어떤 경우든 내부 정보가 없어야 한다.
        MvcResult result = getJson("/api/v1/does-not-exist");

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("Exception");
        assertThat(content).doesNotContain("at com.babplanner");
        assertThat(content).doesNotContain("org.hibernate");
    }
}

package com.babplanner.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Gender;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class UserApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("getMe는 인증된 사용자 정보를 돌려준다")
    void getMeReturnsCurrentUser() throws Exception {
        JsonNode auth = signup();

        MvcResult result = getJson("/api/v1/users/me", accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode user = body(result).path("data");
        assertThat(user.path("phone").asText()).isEqualTo(PHONE);
        assertThat(user.path("studentId").asText()).isEqualTo(STUDENT_ID);
        assertThat(user.path("name").asText()).isEqualTo("홍길동");
        assertThat(user.path("role").asText()).isEqualTo("USER");
    }

    @Test
    @DisplayName("getMe 응답에 passwordHash가 없다")
    void getMeHidesPasswordHash() throws Exception {
        JsonNode auth = signup();

        MvcResult result = getJson("/api/v1/users/me", accessTokenOf(auth));

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("passwordHash");
        assertThat(content).doesNotContain("password");
    }

    @Test
    @DisplayName("각 사용자는 자기 정보만 본다")
    void getMeIsScopedToPrincipal() throws Exception {
        JsonNode first = signup();
        JsonNode second = signup("01098765432", "20205678");

        JsonNode firstUser = body(getJson("/api/v1/users/me", accessTokenOf(first))).path("data");
        JsonNode secondUser = body(getJson("/api/v1/users/me", accessTokenOf(second))).path("data");

        assertThat(firstUser.path("phone").asText()).isEqualTo(PHONE);
        assertThat(secondUser.path("phone").asText()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("이름을 수정할 수 있다")
    void updateName() throws Exception {
        JsonNode auth = signup();

        MvcResult result = patchJson(
                "/api/v1/users/me", Map.of("name", "김철수"), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("name").asText()).isEqualTo("김철수");
        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("성별을 수정할 수 있다")
    void updateGender() throws Exception {
        JsonNode auth = signup();

        MvcResult result = patchJson(
                "/api/v1/users/me", Map.of("name", "홍길동", "gender", "FEMALE"), accessTokenOf(auth));

        assertThat(body(result).path("data").path("gender").asText()).isEqualTo("FEMALE");
        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getGender())
                .isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("gender를 null로 보내면 해제된다")
    void clearGender() throws Exception {
        JsonNode auth = signup();
        patchJson("/api/v1/users/me", Map.of("gender", "MALE"), accessTokenOf(auth));

        Map<String, Object> payload = new HashMap<>();
        payload.put("gender", null);
        MvcResult result = patchJson("/api/v1/users/me", payload, accessTokenOf(auth));

        assertThat(body(result).path("data").path("gender").isNull()).isTrue();
    }

    @Test
    @DisplayName("요청에 phone을 넣어도 무시된다")
    void phoneCannotBeUpdated() throws Exception {
        JsonNode auth = signup();

        patchJson(
                "/api/v1/users/me",
                Map.of("name", "김철수", "phone", "01011112222"),
                accessTokenOf(auth));

        assertThat(userRepository.findByPhone(PHONE)).isPresent();
        assertThat(userRepository.findByPhone("01011112222")).isEmpty();
    }

    @Test
    @DisplayName("요청에 studentId를 넣어도 무시된다")
    void studentIdCannotBeUpdated() throws Exception {
        JsonNode auth = signup();

        patchJson(
                "/api/v1/users/me",
                Map.of("name", "김철수", "studentId", "20999999"),
                accessTokenOf(auth));

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getStudentId())
                .isEqualTo(STUDENT_ID);
    }

    @Test
    @DisplayName("요청에 role을 넣어도 권한이 바뀌지 않는다")
    void roleCannotBeEscalated() throws Exception {
        JsonNode auth = signup();

        patchJson(
                "/api/v1/users/me", Map.of("name", "김철수", "role", "ADMIN"), accessTokenOf(auth));

        UserEntity user = userRepository.findByPhone(PHONE).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("요청에 password를 넣어도 비밀번호가 바뀌지 않는다")
    void passwordCannotBeUpdatedHere() throws Exception {
        JsonNode auth = signup();
        String before = userRepository.findByPhone(PHONE).orElseThrow().getPasswordHash();

        patchJson(
                "/api/v1/users/me",
                Map.of("name", "김철수", "password", "another-password"),
                accessTokenOf(auth));

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getPasswordHash())
                .isEqualTo(before);
    }

    @Test
    @DisplayName("수정에도 인증이 필요하다")
    void updateRequiresAuthentication() throws Exception {
        signup();

        MvcResult result = perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/users/me")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김철수\"}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}

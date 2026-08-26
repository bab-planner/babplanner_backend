package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AdminInquiryApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/inquiries";

    private long createInquiry(String userToken, String title) throws Exception {
        MvcResult result = postJson(
                "/api/v1/inquiries",
                Map.of("category", "MENU", "title", title, "content", "본문입니다."),
                userToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    /** 필드를 아예 보내지 않는 부분 수정. Map은 null 값을 담을 수 있다. */
    private MvcResult patchInquiry(long id, Map<String, Object> body, String token)
            throws Exception {
        return perform(patch(PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private List<String> titlesOf(JsonNode data) {
        List<String> titles = new ArrayList<>();
        data.forEach(node -> titles.add(node.path("title").asText()));
        return titles;
    }

    @Test
    @DisplayName("관리자는 모든 사용자의 문의를 본다")
    void adminSeesEveryInquiry() throws Exception {
        createInquiry(accessTokenOf(signup()), "첫 사용자");
        createInquiry(accessTokenOf(signup("01098765432", "20205678")), "둘째 사용자");

        JsonNode data = body(getJson(PATH, adminToken())).path("data");

        assertThat(titlesOf(data)).containsExactlyInAnyOrder("첫 사용자", "둘째 사용자");
    }

    @Test
    @DisplayName("상태로 걸러 본다")
    void filtersByStatus() throws Exception {
        String user = accessTokenOf(signup());
        long answered = createInquiry(user, "답변한 문의");
        createInquiry(user, "접수만 된 문의");
        String admin = adminToken();
        patchInquiry(answered, Map.of("status", "ANSWERED"), admin);

        JsonNode data = body(getJson(PATH + "?status=ANSWERED", admin)).path("data");

        assertThat(titlesOf(data)).containsExactly("답변한 문의");
    }

    @Test
    @DisplayName("목록은 최신순이다")
    void listIsNewestFirst() throws Exception {
        String user = accessTokenOf(signup());
        createInquiry(user, "먼저");
        createInquiry(user, "나중");

        JsonNode data = body(getJson(PATH, adminToken())).path("data");

        assertThat(titlesOf(data)).containsExactly("나중", "먼저");
    }

    @Test
    @DisplayName("남의 문의 상세도 관리자는 본다")
    void adminReadsAnyDetail() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "상세 확인");

        MvcResult result = getJson(PATH + "/" + id, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("title").asText()).isEqualTo("상세 확인");
    }

    @Test
    @DisplayName("없는 문의는 404다")
    void unknownInquiryIsNotFound() throws Exception {
        String admin = adminToken();

        assertThat(getJson(PATH + "/999999", admin).getResponse().getStatus()).isEqualTo(404);
        assertThat(patchInquiry(999999L, Map.of("status", "CLOSED"), admin)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("상태만 바꾼다")
    void patchesStatusOnly() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        JsonNode data = body(patchInquiry(id, Map.of("status", "IN_PROGRESS"), adminToken()))
                .path("data");

        assertThat(data.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(data.path("adminAnswer").isNull()).isTrue();
    }

    @Test
    @DisplayName("답변만 쓴다")
    void patchesAnswerOnly() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        JsonNode data = body(patchInquiry(id, Map.of("adminAnswer", "확인했습니다."), adminToken()))
                .path("data");

        assertThat(data.path("adminAnswer").asText()).isEqualTo("확인했습니다.");
        assertThat(data.path("answeredAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("답변을 쓰면 답변 시각이 남는다")
    void answeredAtIsRecorded() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        JsonNode data = body(patchInquiry(id, Map.of("adminAnswer", "답변"), adminToken()))
                .path("data");

        assertThat(java.time.Instant.parse(data.path("answeredAt").asText()))
                .isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("답변을 지우면 답변 시각도 사라진다")
    void clearingAnswerClearsTheTime() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");
        String admin = adminToken();
        patchInquiry(id, Map.of("adminAnswer", "답변"), admin);

        Map<String, Object> clear = new HashMap<>();
        clear.put("adminAnswer", null);
        JsonNode data = body(patchInquiry(id, clear, admin)).path("data");

        // 남겨 두면 "답변했는데 내용이 없다"는 상태가 된다.
        assertThat(data.path("adminAnswer").isNull()).isTrue();
        assertThat(data.path("answeredAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("상태만 보낸 요청이 답변을 지우지 않는다")
    void statusOnlyPatchKeepsTheAnswer() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");
        String admin = adminToken();
        patchInquiry(id, Map.of("adminAnswer", "답변"), admin);

        JsonNode data = body(patchInquiry(id, Map.of("status", "CLOSED"), admin)).path("data");

        // 필드를 안 보낸 것과 null을 보낸 것은 다르다.
        assertThat(data.path("adminAnswer").asText()).isEqualTo("답변");
        assertThat(data.path("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("답변만 보낸 요청이 상태를 바꾸지 않는다")
    void answerDoesNotChangeTheStatus() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        JsonNode data = body(patchInquiry(id, Map.of("adminAnswer", "답변"), adminToken()))
                .path("data");

        // 답변을 적어 두고 아직 검토 중일 수 있다. 상태는 관리자가 정한다.
        assertThat(data.path("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("아무것도 바꾸지 않는 요청은 거부한다")
    void emptyPatchIsRejected() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        MvcResult result = patchInquiry(id, Map.of(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("EMPTY_UPDATE");
    }

    @Test
    @DisplayName("둘 다 보내면 둘 다 바뀐다")
    void bothFieldsCanChangeTogether() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        JsonNode data = body(patchInquiry(
                        id, Map.of("status", "ANSWERED", "adminAnswer", "처리했습니다."), adminToken()))
                .path("data");

        assertThat(data.path("status").asText()).isEqualTo("ANSWERED");
        assertThat(data.path("adminAnswer").asText()).isEqualTo("처리했습니다.");
    }

    @Test
    @DisplayName("알 수 없는 상태는 400이다")
    void unknownStatusIsRejected() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");

        MvcResult result = patchInquiry(id, Map.of("status", "PENDING"), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("네 상태를 모두 쓸 수 있다")
    void allFourStatusesAreAccepted() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "문의");
        String admin = adminToken();

        for (InquiryStatus status : InquiryStatus.values()) {
            JsonNode data = body(patchInquiry(id, Map.of("status", status.name()), admin))
                    .path("data");
            assertThat(data.path("status").asText()).isEqualTo(status.name());
        }
    }

    @Test
    @DisplayName("사용자도 바뀐 내용을 본다")
    void userSeesTheAdminUpdate() throws Exception {
        String user = accessTokenOf(signup());
        long id = createInquiry(user, "문의");
        patchInquiry(id, Map.of("status", "ANSWERED", "adminAnswer", "답변드립니다."), adminToken());

        JsonNode detail = body(getJson("/api/v1/inquiries/" + id, user)).path("data");
        JsonNode listed = body(getJson("/api/v1/inquiries", user)).path("data").get(0);

        assertThat(detail.path("status").asText()).isEqualTo("ANSWERED");
        assertThat(detail.path("adminAnswer").asText()).isEqualTo("답변드립니다.");
        assertThat(detail.path("answeredAt").isNull()).isFalse();
        assertThat(listed.path("adminAnswer").asText()).isEqualTo("답변드립니다.");
    }

    @Test
    @DisplayName("사용자는 여전히 남의 문의를 볼 수 없다")
    void usersStillCannotReadOthersInquiries() throws Exception {
        long id = createInquiry(accessTokenOf(signup()), "남의 문의");
        String other = accessTokenOf(signup("01098765432", "20205678"));

        assertThat(getJson("/api/v1/inquiries/" + id, other).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(body(getJson("/api/v1/inquiries", other)).path("data")).isEmpty();
    }

    @Test
    @DisplayName("사용자는 답변 API를 쓸 수 없다")
    void usersCannotAnswer() throws Exception {
        String user = accessTokenOf(signup());
        long id = createInquiry(user, "문의");

        assertThat(patchInquiry(id, Map.of("adminAnswer", "내가 답한다"), user)
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(inquiryRepository.findById(id).orElseThrow().getAdminAnswer()).isNull();
    }
}

package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.babplanner.backend.notice.repository.NoticeRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class AdminNoticeApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/notices";

    @Autowired private NoticeRepository noticeRepository;

    /**
     * 이 테스트가 만든 공지에만 붙이는 표시.
     *
     * <p>씨앗 공지를 지우면 그것을 세는 다른 테스트가 무너진다. 공용 정리는
     * 공지를 건드리지 않으므로 지운 것이 그대로 남는다. 지우는 대신 내 것만
     * 골라 본다.
     */
    private static final String MARK = "[관리자테스트] ";

    @BeforeEach
    @AfterEach
    void removeNoticesCreatedHere() {
        noticeRepository.deleteAll(noticeRepository
                .findAllByOrderByImportantDescPublishedAtDescIdDesc()
                .stream()
                .filter(notice -> notice.getTitle().startsWith(MARK))
                .toList());
        noticeRepository.flush();
    }

    private Map<String, Object> payload(Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", MARK + "점심 운영 안내");
        body.put("content", "다음 주 월요일은 휴무입니다.");
        body.putAll(overrides);
        return body;
    }

    private JsonNode created(String token, Map<String, Object> overrides) throws Exception {
        MvcResult result = postJson(PATH, payload(overrides), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data");
    }

    /** 이 테스트가 만든 공지의 제목만. 표시는 떼고 돌려준다. */
    private List<String> myTitlesOf(JsonNode data) {
        List<String> titles = new ArrayList<>();
        data.forEach(node -> {
            String title = node.path("title").asText();
            if (title.startsWith(MARK)) {
                titles.add(title.substring(MARK.length()));
            }
        });
        return titles;
    }

    private int myCountOf(JsonNode data) {
        return myTitlesOf(data).size();
    }

    @Test
    @DisplayName("관리자 목록은 공개되지 않은 공지도 보여준다")
    void adminListIncludesUnpublished() throws Exception {
        String token = adminToken();
        created(token, Map.of("title", MARK + "공개", "published", true));
        created(token, Map.of("title", MARK + "작성중", "published", false));

        JsonNode data = body(getJson(PATH, token)).path("data");

        assertThat(myTitlesOf(data)).containsExactlyInAnyOrder("공개", "작성중");
    }

    @Test
    @DisplayName("등록은 201이고 기본값은 공개다")
    void createDefaultsToPublished() throws Exception {
        JsonNode data = created(adminToken(), Map.of());

        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("important").asBoolean()).isFalse();
        assertThat(noticeRepository.findById(data.path("id").asLong()).orElseThrow().isPublished())
                .isTrue();
    }

    @Test
    @DisplayName("공개하지 않은 공지를 만들 수 있다")
    void createUnpublished() throws Exception {
        JsonNode data = created(adminToken(), Map.of("published", false));

        assertThat(noticeRepository.findById(data.path("id").asLong()).orElseThrow().isPublished())
                .isFalse();
    }

    @Test
    @DisplayName("공개하지 않은 공지는 사용자 목록에 없다")
    void unpublishedIsHiddenFromTheUserList() throws Exception {
        String token = adminToken();
        created(token, Map.of("title", MARK + "공개", "published", true));
        created(token, Map.of("title", MARK + "작성중", "published", false));

        JsonNode data = body(getJson("/api/v1/notices")).path("data");

        assertThat(myTitlesOf(data)).containsExactly("공개");
    }

    @Test
    @DisplayName("공개하지 않은 공지는 사용자 상세에서 404다")
    void unpublishedDetailIsNotFoundForUsers() throws Exception {
        long id = created(adminToken(), Map.of("published", false)).path("id").asLong();

        MvcResult result = getJson("/api/v1/notices/" + id);

        // 주소를 직접 알아도 볼 수 없어야 한다.
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("NOTICE_NOT_FOUND");
    }

    @Test
    @DisplayName("수정은 200이고 전체를 교체한다")
    void updateReplacesEverything() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of("important", true)).path("id").asLong();

        MvcResult result = putJson(
                PATH + "/" + id,
                payload(Map.of("title", MARK + "바뀐 제목", "content", "바뀐 본문")),
                token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("title").asText()).isEqualTo(MARK + "바뀐 제목");
        // important를 보내지 않았으므로 계약 기본값 false가 된다.
        assertThat(data.path("important").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("공개로 바꾸면 사용자에게 보인다")
    void publishingMakesItVisible() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of("published", false)).path("id").asLong();
        assertThat(myCountOf(body(getJson("/api/v1/notices")).path("data"))).isZero();

        putJson(PATH + "/" + id, payload(Map.of("published", true)), token);

        assertThat(myCountOf(body(getJson("/api/v1/notices")).path("data"))).isEqualTo(1);
    }

    @Test
    @DisplayName("비공개로 바꾸면 사용자에게서 사라진다")
    void unpublishingHidesIt() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of("published", true)).path("id").asLong();
        assertThat(myCountOf(body(getJson("/api/v1/notices")).path("data"))).isEqualTo(1);

        putJson(PATH + "/" + id, payload(Map.of("published", false)), token);

        assertThat(myCountOf(body(getJson("/api/v1/notices")).path("data"))).isZero();
        assertThat(getJson("/api/v1/notices/" + id).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("공개를 껐다 켜도 게시 시각은 그대로다")
    void publishedAtSurvivesToggling() throws Exception {
        String token = adminToken();
        JsonNode created = created(token, Map.of("published", true));
        long id = created.path("id").asLong();
        String publishedAt = created.path("publishedAt").asText();

        // 토큰 수명 안에서만 민다. 더 밀면 관리자 토큰이 만료돼 401이 된다.
        clock.advance(java.time.Duration.ofMinutes(5));
        putJson(PATH + "/" + id, payload(Map.of("published", false)), token);
        JsonNode after = body(putJson(PATH + "/" + id, payload(Map.of("published", true)), token))
                .path("data");

        // 제목을 고쳤다고 글 쓴 시각이 오늘로 바뀌면 목록 순서가 흔들린다.
        assertThat(after.path("publishedAt").asText()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("삭제는 204다")
    void deleteReturnsNoContent() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of()).path("id").asLong();

        MvcResult result = perform(delete(PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(noticeRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("없는 공지는 404다")
    void unknownNoticeIsNotFound() throws Exception {
        String token = adminToken();

        assertThat(putJson(PATH + "/999999", payload(Map.of()), token).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(perform(delete(PATH + "/999999")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("사용자 목록 정렬이 그대로다")
    void publicOrderingIsUnchanged() throws Exception {
        String token = adminToken();
        created(token, Map.of("title", MARK + "먼저 쓴 일반"));
        clock.advance(java.time.Duration.ofMinutes(1));
        created(token, Map.of("title", MARK + "나중 쓴 일반"));
        clock.advance(java.time.Duration.ofMinutes(1));
        created(token, Map.of("title", MARK + "중요", "important", true));

        JsonNode data = body(getJson("/api/v1/notices")).path("data");

        // 중요 먼저, 그다음 최신순.
        assertThat(myTitlesOf(data)).containsExactly("중요", "나중 쓴 일반", "먼저 쓴 일반");
    }

    @Test
    @DisplayName("빈 제목이나 본문은 400이다")
    void blankFieldsAreRejected() throws Exception {
        String token = adminToken();

        assertThat(postJson(PATH, payload(Map.of("title", "")), token).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(postJson(PATH, payload(Map.of("content", "")), token).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("응답이 공개 여부를 담는다")
    void responseCarriesPublished() throws Exception {
        String token = adminToken();

        // 이 값이 없으면 관리 목록만 보고는 어느 공지가 사용자에게 보이는지
        // 알 수 없다. 미공개 공지가 섞여 있는 목록이라 더 그렇다.
        assertThat(created(token, Map.of("published", true)).path("published").asBoolean())
                .isTrue();
        assertThat(created(token, Map.of("published", false)).path("published").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("목록과 수정 응답에도 공개 여부가 있다")
    void listAndUpdateCarryPublished() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of("title", MARK + "작성중", "published", false))
                .path("id")
                .asLong();

        JsonNode listed = body(getJson(PATH, token)).path("data");
        JsonNode mine = null;
        for (JsonNode notice : listed) {
            if (notice.path("id").asLong() == id) {
                mine = notice;
            }
        }

        assertThat(mine).isNotNull();
        assertThat(mine.path("published").asBoolean()).isFalse();
        assertThat(body(putJson(PATH + "/" + id, payload(Map.of("published", true)), token))
                        .path("data")
                        .path("published")
                        .asBoolean())
                .isTrue();
    }
}

package com.babplanner.backend.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class NoticeApiTest extends ApiIntegrationTest {

    private JsonNode listData() throws Exception {
        return body(getJson("/api/v1/notices")).path("data");
    }

    @Test
    @DisplayName("익명으로 목록을 조회한다")
    void listIsAnonymous() throws Exception {
        MvcResult result = getJson("/api/v1/notices");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        assertThat(json.path("data")).hasSize(4);
    }

    @Test
    @DisplayName("중요 공지가 먼저 온다")
    void importantComesFirst() throws Exception {
        JsonNode data = listData();

        List<Boolean> flags = new ArrayList<>();
        data.forEach(node -> flags.add(node.path("important").asBoolean()));
        assertThat(flags).containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("같은 중요도 안에서는 최신순이다")
    void newestFirstWithinSameImportance() throws Exception {
        JsonNode data = listData();

        List<Instant> important = new ArrayList<>();
        List<Instant> normal = new ArrayList<>();
        data.forEach(node -> {
            Instant publishedAt = Instant.parse(node.path("publishedAt").asText());
            if (node.path("important").asBoolean()) {
                important.add(publishedAt);
            } else {
                normal.add(publishedAt);
            }
        });

        assertThat(important).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(normal).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("일반 공지도 목록에 포함된다")
    void includesNonImportantNotices() throws Exception {
        JsonNode data = listData();

        List<String> titles = new ArrayList<>();
        data.forEach(node -> titles.add(node.path("title").asText()));
        assertThat(titles).contains("[개발 샘플] 일반 공지 최신", "[개발 샘플] 일반 공지 이전");
    }

    @Test
    @DisplayName("정렬은 매번 같다")
    void orderIsDeterministic() throws Exception {
        List<Long> first = new ArrayList<>();
        listData().forEach(node -> first.add(node.path("id").asLong()));
        List<Long> second = new ArrayList<>();
        listData().forEach(node -> second.add(node.path("id").asLong()));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("응답 키가 OpenAPI Notice와 같다")
    void keysMatchContract() throws Exception {
        JsonNode item = listData().get(0);

        assertThat(item.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "title", "content", "important", "publishedAt");
    }

    @Test
    @DisplayName("본문 줄바꿈을 보존한다")
    void preservesLineBreaks() throws Exception {
        long id = 0;
        for (JsonNode node : listData()) {
            if (node.path("title").asText().contains("시스템 점검")) {
                id = node.path("id").asLong();
            }
        }
        assertThat(id).isPositive();

        JsonNode data = body(getJson("/api/v1/notices/" + id)).path("data");

        assertThat(data.path("content").asText()).contains("\n");
        assertThat(data.path("content").asText()).contains("두 번째 문단");
    }

    @Test
    @DisplayName("publishedAt은 ISO-8601 UTC다")
    void publishedAtIsIso8601() throws Exception {
        for (JsonNode node : listData()) {
            String publishedAt = node.path("publishedAt").asText();
            assertThat(publishedAt).endsWith("Z");
            assertThat(Instant.parse(publishedAt)).isNotNull();
        }
    }

    @Test
    @DisplayName("상세를 실제 id로 조회한다")
    void detailByServerId() throws Exception {
        long id = listData().get(0).path("id").asLong();

        MvcResult result = getJson("/api/v1/notices/" + id);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("존재하지 않는 공지는 404다")
    void detailNotFound() throws Exception {
        MvcResult result = getJson("/api/v1/notices/999999");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("NOTICE_NOT_FOUND");
    }

    @Test
    @DisplayName("pagination 파라미터를 계약에 추가하지 않았다")
    void noPagination() throws Exception {
        // page/size를 줘도 무시되고 전체가 나온다. 응답에도 페이지 메타가 없다.
        JsonNode json = body(getJson("/api/v1/notices?page=0&size=1"));

        assertThat(json.path("data")).hasSize(4);
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("success", "data", "error");
    }

    @Test
    @DisplayName("공지 쓰기 API를 만들지 않았다")
    void noWriteApi() throws Exception {
        assertThat(postJson("/api/v1/notices", java.util.Map.of()).getResponse().getStatus())
                .isEqualTo(401);
    }
}

package com.babplanner.backend.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.inquiry.domain.InquiryCategory;
import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class InquiryApiTest extends ApiIntegrationTest {

    private Map<String, Object> payload(String category, String title) {
        return Map.of("category", category, "title", title, "content", "문의 본문입니다.");
    }

    private long createInquiry(JsonNode auth, String category, String title) throws Exception {
        MvcResult result =
                postJson("/api/v1/inquiries", payload(category, title), accessTokenOf(auth));
        return body(result).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("목록은 인증이 필요하다")
    void listRequiresAuth() throws Exception {
        assertThat(getJson("/api/v1/inquiries").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("등록은 인증이 필요하다")
    void createRequiresAuth() throws Exception {
        assertThat(postJson("/api/v1/inquiries", payload("MENU", "제목")).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("등록은 201이고 계약 키를 돌려준다")
    void createReturnsContractShape() throws Exception {
        JsonNode auth = signup();

        MvcResult result =
                postJson("/api/v1/inquiries", payload("FACILITY", "의자가 흔들려요"), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(result).path("data");
        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "category",
                        "title",
                        "content",
                        "status",
                        "adminAnswer",
                        "createdAt",
                        "answeredAt");
        assertThat(data.path("category").asText()).isEqualTo("FACILITY");
        assertThat(data.path("title").asText()).isEqualTo("의자가 흔들려요");
    }

    @Test
    @DisplayName("초기 상태는 RECEIVED이고 답변 필드는 null이다")
    void initialStateIsReceived() throws Exception {
        JsonNode auth = signup();

        JsonNode data = body(postJson("/api/v1/inquiries", payload("MENU", "제목"), accessTokenOf(auth)))
                .path("data");

        assertThat(data.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(data.path("adminAnswer").isNull()).isTrue();
        assertThat(data.path("answeredAt").isNull()).isTrue();
        assertThat(data.path("createdAt").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("5개 category를 모두 받는다")
    void allCategoriesAccepted() throws Exception {
        JsonNode auth = signup();
        List<String> categories =
                List.of("MENU", "ALLERGY", "FACILITY", "SERVICE", "OTHER");

        for (String category : categories) {
            MvcResult result =
                    postJson("/api/v1/inquiries", payload(category, category), accessTokenOf(auth));
            assertThat(result.getResponse().getStatus()).as("%s", category).isEqualTo(201);
        }

        JsonNode data = body(getJson("/api/v1/inquiries", accessTokenOf(auth))).path("data");
        List<String> stored = new ArrayList<>();
        data.forEach(node -> stored.add(node.path("category").asText()));
        assertThat(stored).containsExactlyInAnyOrderElementsOf(categories);
    }

    @Test
    @DisplayName("알 수 없는 category는 400이다")
    void unknownCategoryRejected() throws Exception {
        JsonNode auth = signup();

        MvcResult result =
                postJson("/api/v1/inquiries", payload("SUGGESTION", "제목"), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("제목이나 내용이 비면 400이다")
    void blankFieldsRejected() throws Exception {
        JsonNode auth = signup();

        MvcResult result = postJson(
                "/api/v1/inquiries",
                Map.of("category", "MENU", "title", "", "content", "본문"),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("요청에 userId를 넣어도 무시된다")
    void userIdInBodyIsIgnored() throws Exception {
        JsonNode owner = signup();
        JsonNode other = signup("01098765432", "20205678");
        long ownerId = owner.path("user").path("id").asLong();

        postJson(
                "/api/v1/inquiries",
                Map.of("category", "MENU", "title", "제목", "content", "본문", "userId", ownerId),
                accessTokenOf(other));

        // 작성자는 principal이다. owner의 목록에는 나타나지 않아야 한다.
        assertThat(body(getJson("/api/v1/inquiries", accessTokenOf(owner))).path("data")).isEmpty();
        assertThat(body(getJson("/api/v1/inquiries", accessTokenOf(other))).path("data")).hasSize(1);
    }

    @Test
    @DisplayName("목록은 최신순이다")
    void listIsNewestFirst() throws Exception {
        JsonNode auth = signup();
        createInquiry(auth, "MENU", "첫 번째");
        createInquiry(auth, "MENU", "두 번째");

        JsonNode data = body(getJson("/api/v1/inquiries", accessTokenOf(auth))).path("data");

        assertThat(data.get(0).path("title").asText()).isEqualTo("두 번째");
        assertThat(data.get(1).path("title").asText()).isEqualTo("첫 번째");
    }

    @Test
    @DisplayName("본인 문의만 목록에 나온다")
    void listIsScopedToUser() throws Exception {
        JsonNode owner = signup();
        createInquiry(owner, "MENU", "내 문의");
        JsonNode other = signup("01098765432", "20205678");

        JsonNode data = body(getJson("/api/v1/inquiries", accessTokenOf(other))).path("data");

        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("본인 문의 상세를 조회한다")
    void detailOfOwnInquiry() throws Exception {
        JsonNode auth = signup();
        long id = createInquiry(auth, "SERVICE", "상세 확인");

        MvcResult result = getJson("/api/v1/inquiries/" + id, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("title").asText()).isEqualTo("상세 확인");
    }

    @Test
    @DisplayName("남의 문의는 404이며 내용이 노출되지 않는다")
    void foreignInquiryIsNotFound() throws Exception {
        JsonNode owner = signup();
        long id = createInquiry(owner, "MENU", "남의 문의 제목");
        JsonNode other = signup("01098765432", "20205678");

        MvcResult result = getJson("/api/v1/inquiries/" + id, accessTokenOf(other));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("남의 문의 제목");
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INQUIRY_NOT_FOUND");
    }

    @Test
    @DisplayName("없는 문의도 404다")
    void unknownInquiryIsNotFound() throws Exception {
        JsonNode auth = signup();

        MvcResult result = getJson("/api/v1/inquiries/999999", accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        // 남의 문의와 같은 응답이어야 존재 여부를 추측할 수 없다.
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INQUIRY_NOT_FOUND");
    }

    @Test
    @DisplayName("상세 조회는 인증이 필요하다")
    void detailRequiresAuth() throws Exception {
        JsonNode auth = signup();
        long id = createInquiry(auth, "MENU", "제목");

        assertThat(getJson("/api/v1/inquiries/" + id).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("답변 작성 API를 만들지 않았다")
    void noAnswerWriteApi() throws Exception {
        JsonNode auth = signup();
        long id = createInquiry(auth, "MENU", "제목");

        MvcResult result = patchJson(
                "/api/v1/inquiries/" + id,
                Map.of("adminAnswer", "답변", "status", "ANSWERED"),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(inquiryRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(InquiryStatus.RECEIVED);
        assertThat(inquiryRepository.findById(id).orElseThrow().getAdminAnswer()).isNull();
    }

    @Test
    @DisplayName("DB에 실제로 저장된다")
    void persistsToDatabase() throws Exception {
        JsonNode auth = signup();
        long id = createInquiry(auth, "ALLERGY", "알레르기 문의");

        var stored = inquiryRepository.findById(id).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(auth.path("user").path("id").asLong());
        assertThat(stored.getCategory()).isEqualTo(InquiryCategory.ALLERGY);
        assertThat(stored.getStatus()).isEqualTo(InquiryStatus.RECEIVED);
    }
}

package com.babplanner.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.common.error.GlobalExceptionHandler;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 입력 크기 제한이 500이 아니라 4xx로 이어지는지 본다. */
class InputLimitsTest extends ApiIntegrationTest {

    private Map<String, Object> inquiry(String title, String content) {
        return Map.of("category", "OTHER", "title", title, "content", content);
    }

    @Test
    @DisplayName("업로드 크기 초과는 422 INVALID_WASTE_IMAGE 봉투다")
    void uploadTooLarge() {
        var response = new GlobalExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_WASTE_IMAGE");
    }

    @Test
    @DisplayName("문의 제목이 200자를 넘으면 400이다")
    void titleTooLong() throws Exception {
        JsonNode auth = signup();

        var result = postJson(
                "/api/v1/inquiries", inquiry("가".repeat(201), "내용"), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("문의 내용이 5000자를 넘으면 400이다")
    void contentTooLong() throws Exception {
        JsonNode auth = signup();

        var result = postJson(
                "/api/v1/inquiries", inquiry("제목", "가".repeat(5001)), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("한도 안의 문의는 저장된다")
    void withinLimitsAccepted() throws Exception {
        JsonNode auth = signup();

        var result = postJson(
                "/api/v1/inquiries",
                inquiry("가".repeat(200), "가".repeat(5000)),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}

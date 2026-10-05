package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** bapplanner.reward.waste-approval-points 설정이 지급액에 반영되는지 본다. */
@TestPropertySource(properties = "bapplanner.reward.waste-approval-points=250")
class WasteApprovalRewardOverrideTest extends ApiIntegrationTest {

    @Autowired private MenuRepository menuRepository;

    @Test
    @DisplayName("관리자가 승인하면 설정한 포인트만큼 지급한다")
    void overrideApplies() throws Exception {
        JsonNode auth = signup();
        long id = body(perform(multipart("/api/v1/waste-submissions")
                        .file(new MockMultipartFile(
                                "image",
                                "lunch.jpg",
                                "image/jpeg",
                                "fake-image".getBytes(StandardCharsets.UTF_8)))
                        .param("menuId", String.valueOf(menuRepository.findAll().getFirst().getId()))
                        .param("capturedAt", "2026-08-26T03:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth))))
                .path("data")
                .path("id")
                .asLong();
        String adminToken = adminToken();
        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/admin/waste-submissions/" + id + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
        JsonNode data = body(getJson("/api/v1/waste-submissions/" + id, accessTokenOf(auth)))
                .path("data");

        assertThat(data.path("earnedPoints").asInt()).isEqualTo(250);
        JsonNode summary = body(getJson("/api/v1/rewards/summary", accessTokenOf(auth))).path("data");
        assertThat(summary.path("points").asInt()).isEqualTo(250);
    }
}

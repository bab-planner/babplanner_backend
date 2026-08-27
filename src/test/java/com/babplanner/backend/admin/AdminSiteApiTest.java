package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자용 식당 목록.
 *
 * <p>관리자 웹이 {@code siteId = 1} 을 코드에 박지 않으려면 이 목록이
 * 필요하다. 내보내는 것은 고르는 데 필요한 최소한이어야 한다.
 */
class AdminSiteApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/sites";

    @Autowired private DiningSiteRepository siteRepository;

    @Test
    @DisplayName("관리자가 식당 목록을 본다")
    void adminReadsTheSites() throws Exception {
        MvcResult result = getJson(PATH, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data).isNotEmpty();
        assertThat(data).hasSize(siteRepository.findAll().size());
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(getJson(PATH, token).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(PATH).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("노출 순서대로 돌려준다")
    void orderingFollowsSortOrder() throws Exception {
        JsonNode data = body(getJson(PATH, adminToken())).path("data");

        List<DiningSite> expected = siteRepository.findAllByOrderBySortOrderAscIdAsc();
        List<Long> returned = new ArrayList<>();
        data.forEach(node -> returned.add(node.path("id").asLong()));

        assertThat(returned).isEqualTo(expected.stream().map(DiningSite::getId).toList());
    }

    @Test
    @DisplayName("id와 이름만 담는다")
    void responseCarriesIdAndNameOnly() throws Exception {
        JsonNode first = body(getJson(PATH, adminToken())).path("data").get(0);

        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name");
        assertThat(first.path("id").asLong()).isPositive();
        assertThat(first.path("name").asText()).isNotBlank();
    }

    @Test
    @DisplayName("내부 식별 코드나 운영 정보를 내보내지 않는다")
    void internalFieldsAreNotExposed() throws Exception {
        String json = getJson(PATH, adminToken()).getResponse().getContentAsString();

        // code는 내부 식별용이다. 한 번 나가면 클라이언트가 그 문자열로
        // 분기하기 시작한다.
        for (String forbidden : List.of("code", "STUDENT", "STAFF", "active", "sortOrder")) {
            assertThat(json).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("식당을 만들거나 고치는 API가 없다")
    void noSiteMutationEndpointExists() throws IOException {
        Path main = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                if (!code.contains("/api/v1/admin/sites")) {
                    continue;
                }
                // 식당은 메뉴·혼잡도·예측이 전부 참조하는 값이다. 화면에서
                // 손대게 할 이유가 없다.
                for (String mutation :
                        List.of("@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping")) {
                    if (code.contains(mutation)) {
                        offenders.add(path + ": " + mutation);
                    }
                }
            }
        }

        assertThat(offenders).isEmpty();
    }
}

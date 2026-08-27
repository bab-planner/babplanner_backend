package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 혼잡도 시계열.
 *
 * <p>저장된 관측만 나온다는 것을 확인한다. 값을 채워 넣거나 구간으로 묶는
 * 순간, 화면에 보이는 그래프와 실제로 관측한 값이 달라진다.
 */
class AdminCongestionApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/congestion/timeseries";
    private static final Instant BASE = Instant.parse("2026-09-15T02:00:00Z");

    @Autowired private CongestionObservationRepository observationRepository;
    @Autowired private DiningSiteRepository siteRepository;

    /** V22가 옮겨 둔 씨앗 관측까지 지우고 이 테스트가 넣은 것만 센다. */
    @BeforeEach
    void clearObservations() {
        observationRepository.deleteAll();
        observationRepository.flush();
    }

    private DiningSite site(int index) {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().get(index);
    }

    private void observe(
            DiningSite site, CongestionLevel level, Integer people, Instant observedAt) {
        observationRepository.save(new CongestionObservation(
                site, level, people, "5분 이내", "TEST", observedAt, BASE));
    }

    private MvcResult query(String queryString, String token) throws Exception {
        return getJson(PATH + "?" + queryString, token);
    }

    private String window() {
        return "from=" + BASE.minusSeconds(3600) + "&to=" + BASE.plusSeconds(3600);
    }

    @Test
    @DisplayName("기간 안의 관측을 시간 순으로 돌려준다")
    void returnsObservationsInOrder() throws Exception {
        DiningSite site = site(0);
        observe(site, CongestionLevel.CROWDED, 90, BASE.plusSeconds(600));
        observe(site, CongestionLevel.COMFORTABLE, 10, BASE);
        observe(site, CongestionLevel.NORMAL, 40, BASE.plusSeconds(300));

        JsonNode data =
                body(query("siteId=" + site.getId() + "&" + window(), adminToken())).path("data");

        assertThat(data).hasSize(3);
        assertThat(List.of(
                        data.get(0).path("peopleCount").asInt(),
                        data.get(1).path("peopleCount").asInt(),
                        data.get(2).path("peopleCount").asInt()))
                .containsExactly(10, 40, 90);
    }

    @Test
    @DisplayName("관측 값을 그대로 내보낸다")
    void returnsStoredValuesUnchanged() throws Exception {
        DiningSite site = site(0);
        observe(site, CongestionLevel.CROWDED, 90, BASE);

        JsonNode point = body(query("siteId=" + site.getId() + "&" + window(), adminToken()))
                .path("data")
                .get(0);

        assertThat(point.path("siteId").asLong()).isEqualTo(site.getId());
        assertThat(point.path("level").asText()).isEqualTo("CROWDED");
        assertThat(point.path("peopleCount").asInt()).isEqualTo(90);
        assertThat(point.path("source").asText()).isEqualTo("TEST");
        assertThat(point.path("observedAt").asText()).isEqualTo(BASE.toString());
    }

    @Test
    @DisplayName("기간 밖의 관측은 빼고 양 끝은 넣는다")
    void rangeIsInclusive() throws Exception {
        DiningSite site = site(0);
        Instant from = BASE;
        Instant to = BASE.plusSeconds(600);
        observe(site, CongestionLevel.NORMAL, 1, from.minusSeconds(1));
        observe(site, CongestionLevel.NORMAL, 2, from);
        observe(site, CongestionLevel.NORMAL, 3, to);
        observe(site, CongestionLevel.NORMAL, 4, to.plusSeconds(1));

        JsonNode data = body(query(
                        "siteId=" + site.getId() + "&from=" + from + "&to=" + to, adminToken()))
                .path("data");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("peopleCount").asInt()).isEqualTo(2);
        assertThat(data.get(1).path("peopleCount").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 식당의 관측을 섞지 않는다")
    void otherSitesAreExcluded() throws Exception {
        observe(site(0), CongestionLevel.NORMAL, 11, BASE);
        observe(site(1), CongestionLevel.NORMAL, 22, BASE);

        JsonNode data = body(query("siteId=" + site(1).getId() + "&" + window(), adminToken()))
                .path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("peopleCount").asInt()).isEqualTo(22);
    }

    @Test
    @DisplayName("siteId를 주지 않으면 대표 식당을 본다")
    void defaultsToRepresentativeSite() throws Exception {
        observe(site(0), CongestionLevel.NORMAL, 11, BASE);
        observe(site(1), CongestionLevel.NORMAL, 22, BASE);

        JsonNode data = body(query(window(), adminToken())).path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("siteId").asLong()).isEqualTo(site(0).getId());
    }

    @Test
    @DisplayName("관측이 없으면 빈 배열이다")
    void emptyWhenNothingObserved() throws Exception {
        MvcResult result = query("siteId=" + site(0).getId() + "&" + window(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("기간이 뒤집히면 422다")
    void reversedRangeIsRejected() throws Exception {
        MvcResult result = query("from=" + BASE.plusSeconds(600) + "&to=" + BASE, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    @DisplayName("from과 to가 같은 순간이어도 그 순간의 관측은 나온다")
    void singleInstantRangeIsAllowed() throws Exception {
        observe(site(0), CongestionLevel.NORMAL, 7, BASE);

        JsonNode data = body(query("from=" + BASE + "&to=" + BASE, adminToken())).path("data");

        assertThat(data).hasSize(1);
    }

    @Test
    @DisplayName("없는 식당을 물으면 422다")
    void unknownSiteIsRejected() throws Exception {
        // 빈 배열로 답하면 오타가 "관측 없음"으로 보인다.
        MvcResult result = query("siteId=999999&" + window(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("UNKNOWN_DINING_SITE");
    }

    @Test
    @DisplayName("from이나 to가 없으면 400이다")
    void missingRangeIsRejected() throws Exception {
        String token = adminToken();

        assertThat(query("from=" + BASE, token).getResponse().getStatus()).isEqualTo(400);
        assertThat(query("to=" + BASE, token).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("시각 형식이 틀리면 400이다")
    void malformedRangeIsRejected() throws Exception {
        assertThat(query("from=yesterday&to=today", adminToken()).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("offset이 붙은 시각도 받는다")
    void acceptsOffsetDateTime() throws Exception {
        // 관리자는 KST로 적어 보낸다. Z만 받으면 매번 변환을 시켜야 한다.
        observe(site(0), CongestionLevel.NORMAL, 7, BASE);

        MvcResult result = perform(get(PATH)
                .param("from", "2026-09-15T10:00:00+09:00")
                .param("to", "2026-09-15T12:00:00+09:00")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).hasSize(1);
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(PATH + "?" + window()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(query(window(), token).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("공개 혼잡도 조회는 그대로다")
    void publicCurrentIsUnaffected() throws Exception {
        // 이력 테이블을 비워도 현재값은 별도 테이블이라 영향받지 않는다.
        assertThat(getJson("/api/v1/congestion/current").getResponse().getStatus()).isEqualTo(200);
    }
}

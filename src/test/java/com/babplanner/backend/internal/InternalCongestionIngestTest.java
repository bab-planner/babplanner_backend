package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import com.babplanner.backend.congestion.service.CongestionIngestService;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 혼잡도 관측 수집.
 *
 * <p>이 경로는 사람이 쓰는 API가 아니다. 확인하려는 것은 두 가지다. 사용자
 * 토큰으로는 들어올 수 없는가, 그리고 이력과 현재값이 갈라지지 않는가.
 */
class InternalCongestionIngestTest extends ApiIntegrationTest {

    private static final String PATH = "/internal/v1/congestion-observations";

    /** src/test/resources/application.yml 의 테스트 전용 가짜 토큰. */
    private static final String SERVICE_TOKEN = "test-only-internal-service-token-not-a-real-secret";

    private static final String OBSERVED_AT = "2026-09-15T02:00:00Z";

    @Autowired private CongestionObservationRepository observationRepository;
    @Autowired private CongestionStatusRepository statusRepository;
    @Autowired private DiningSiteRepository siteRepository;

    /** 씨앗이 넣어 둔 현재값. 뒷정리에서 이 상태로 되돌린다. */
    private static final Instant SEED_OBSERVED_AT = Instant.parse("2026-08-26T02:30:00Z");

    /** 테스트를 시작할 때의 현재값 시각. 어떤 테스트가 먼저 돌든 같아야 한다. */
    private static final Instant BASELINE_OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * 이 테스트가 넣은 관측만 세도록 씨앗 이력을 비우고, 현재값을 오래된
     * 시각으로 되돌린다.
     *
     * <p>현재값을 되돌리지 않으면 앞선 테스트가 남긴 최신 시각 때문에 뒤에
     * 도는 테스트의 관측이 "과거"로 취급되어, 실행 순서에 따라 결과가 달라진다.
     * 씨앗 행을 지우지는 않는다. 지우면 그 행을 세는 다른 테스트가 무너진다.
     */
    @BeforeEach
    void resetCongestionState() {
        observationRepository.deleteAll();
        observationRepository.flush();
        rewindCurrentStatus(BASELINE_OBSERVED_AT);
    }

    @AfterEach
    void restoreSeedStatus() {
        rewindCurrentStatus(SEED_OBSERVED_AT);
    }

    private void rewindCurrentStatus(Instant observedAt) {
        statusRepository
                .findBySiteId(siteId())
                .ifPresent(status -> {
                    status.apply(
                            CongestionLevel.NORMAL, 42, "5분 이내", "DEV_SEED", observedAt, observedAt);
                    statusRepository.save(status);
                });
        statusRepository.flush();
    }

    private long siteId() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst().getId();
    }

    private Map<String, Object> payload(Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>();
        body.put("siteId", siteId());
        body.put("deviceId", "cam-lobby-01");
        body.put("peopleCount", 42);
        body.put("level", "NORMAL");
        body.put("observedAt", OBSERVED_AT);
        body.putAll(overrides);
        return body;
    }

    private MvcResult ingest(Map<String, Object> body, String serviceToken) throws Exception {
        var request = post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (serviceToken != null) {
            request = request.header("X-Service-Token", serviceToken);
        }
        return perform(request);
    }

    private MvcResult ingest(Map<String, Object> overrides) throws Exception {
        return ingest(payload(overrides), SERVICE_TOKEN);
    }

    private CongestionObservation onlyObservation() {
        List<CongestionObservation> all = observationRepository.findAll();
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    // ---------- 서비스 토큰 ----------

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void missingTokenIsRejected() throws Exception {
        assertThat(ingest(payload(Map.of()), null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("토큰이 틀리면 401이다")
    void wrongTokenIsRejected() throws Exception {
        MvcResult result = ingest(payload(Map.of()), "wrong-token-value-that-is-long-enough-here");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("없는 토큰과 틀린 토큰이 같은 응답이다")
    void missingAndWrongLookTheSame() throws Exception {
        // 어느 쪽인지 알려주면 유효한 헤더 이름을 찾는 데 쓸 수 있다.
        String missing = ingest(payload(Map.of()), null).getResponse().getContentAsString();
        String wrong = ingest(payload(Map.of()), "wrong-token-value-that-is-long-enough-here")
                .getResponse()
                .getContentAsString();

        assertThat(missing).isEqualTo(wrong);
    }

    @Test
    @DisplayName("맞는 토큰이면 202다")
    void correctTokenIsAccepted() throws Exception {
        assertThat(ingest(Map.of()).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("응답이 토큰을 되비추지 않는다")
    void tokenIsNeverEchoed() throws Exception {
        String body = ingest(Map.of()).getResponse().getContentAsString();

        assertThat(body).doesNotContain(SERVICE_TOKEN);
    }

    @Test
    @DisplayName("일반 사용자 JWT로는 들어올 수 없다")
    void userJwtCannotEnter() throws Exception {
        String token = accessTokenOf(signup());

        MvcResult result = perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload(Map.of()))));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(observationRepository.count()).isZero();
    }

    @Test
    @DisplayName("관리자 JWT만으로도 들어올 수 없다")
    void adminJwtAloneCannotEnter() throws Exception {
        // 관리자 계정 하나가 새면 수집 경로까지 열리는 구조를 만들지 않는다.
        String token = adminToken();

        MvcResult result = perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload(Map.of()))));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(observationRepository.count()).isZero();
    }

    @Test
    @DisplayName("X-Role 같은 헤더는 무시한다")
    void fakeRoleHeaderIsIgnored() throws Exception {
        MvcResult result = perform(post(PATH)
                .header("X-Role", "INTERNAL_SERVICE")
                .header("X-Internal", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload(Map.of()))));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- 저장 ----------

    @Test
    @DisplayName("관측을 이력에 남긴다")
    void observationIsStored() throws Exception {
        ingest(Map.of());

        CongestionObservation stored = onlyObservation();
        assertThat(stored.getPeopleCount()).isEqualTo(42);
        assertThat(stored.getLevel().name()).isEqualTo("NORMAL");
        assertThat(stored.getObservedAt()).isEqualTo(Instant.parse(OBSERVED_AT));
    }

    @Test
    @DisplayName("장비 식별자와 모델 버전을 남긴다")
    void auditMetadataIsStored() throws Exception {
        ingest(Map.of("deviceId", "cam-hall-07", "modelVersion", "vision-v2"));

        CongestionObservation stored = onlyObservation();
        assertThat(stored.getDeviceId()).isEqualTo("cam-hall-07");
        assertThat(stored.getModelVersion()).isEqualTo("vision-v2");
    }

    @Test
    @DisplayName("모델 버전은 없어도 된다")
    void modelVersionIsOptional() throws Exception {
        assertThat(ingest(Map.of()).getResponse().getStatus()).isEqualTo(202);
        assertThat(onlyObservation().getModelVersion()).isNull();
    }

    @Test
    @DisplayName("출처는 서버가 정한다")
    void sourceIsServerControlled() throws Exception {
        // 보내는 쪽이 정하게 하면 저장된 출처를 믿을 수 없다.
        ingest(Map.of("source", "TRUST_ME"));

        assertThat(onlyObservation().getSource()).isEqualTo(CongestionIngestService.SOURCE);
    }

    @Test
    @DisplayName("현재값도 같이 갱신한다")
    void currentStatusIsUpdated() throws Exception {
        ingest(Map.of("peopleCount", 91, "level", "CROWDED"));

        var current = statusRepository.findBySiteId(siteId()).orElseThrow();
        assertThat(current.getPeopleCount()).isEqualTo(91);
        assertThat(current.getLevel().name()).isEqualTo("CROWDED");
        assertThat(current.getObservedAt()).isEqualTo(Instant.parse(OBSERVED_AT));
    }

    @Test
    @DisplayName("이력과 현재값이 함께 남거나 함께 없다")
    void historyAndCurrentMoveTogether() throws Exception {
        ingest(Map.of("peopleCount", 77));

        var current = statusRepository.findBySiteId(siteId()).orElseThrow();
        assertThat(onlyObservation().getPeopleCount()).isEqualTo(current.getPeopleCount());
        assertThat(onlyObservation().getObservedAt()).isEqualTo(current.getObservedAt());
    }

    @Test
    @DisplayName("거절된 요청은 아무것도 남기지 않는다")
    void rejectedRequestStoresNothing() throws Exception {
        long statusBefore = statusRepository.count();

        ingest(Map.of("siteId", 999999L));

        assertThat(observationRepository.count()).isZero();
        assertThat(statusRepository.count()).isEqualTo(statusBefore);
    }

    // ---------- 순서가 뒤집힌 관측 ----------

    @Test
    @DisplayName("더 새로운 관측이 현재값을 갱신한다")
    void newerObservationUpdatesCurrent() throws Exception {
        ingest(Map.of("peopleCount", 10, "observedAt", "2026-09-15T02:00:00Z"));
        ingest(Map.of("peopleCount", 90, "observedAt", "2026-09-15T02:10:00Z"));

        assertThat(statusRepository.findBySiteId(siteId()).orElseThrow().getPeopleCount())
                .isEqualTo(90);
    }

    @Test
    @DisplayName("늦게 도착한 과거 관측이 현재를 되돌리지 않는다")
    void olderObservationDoesNotRollBackCurrent() throws Exception {
        ingest(Map.of("peopleCount", 90, "observedAt", "2026-09-15T02:10:00Z"));
        ingest(Map.of("peopleCount", 10, "observedAt", "2026-09-15T02:00:00Z"));

        // 네트워크가 복구되며 밀린 관측이 한꺼번에 오면, 마지막에 도착한
        // 것으로 덮는 순간 화면의 "지금"이 10분 전으로 돌아간다.
        var current = statusRepository.findBySiteId(siteId()).orElseThrow();
        assertThat(current.getPeopleCount()).isEqualTo(90);
        assertThat(current.getObservedAt()).isEqualTo(Instant.parse("2026-09-15T02:10:00Z"));
    }

    @Test
    @DisplayName("늦게 도착한 과거 관측도 이력에는 남는다")
    void olderObservationStaysInHistory() throws Exception {
        ingest(Map.of("peopleCount", 90, "observedAt", "2026-09-15T02:10:00Z"));
        ingest(Map.of("peopleCount", 10, "observedAt", "2026-09-15T02:00:00Z"));

        // 순서가 뒤집혀 도착했다는 사실 자체가 장비를 의심할 근거다.
        assertThat(observationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 시각이면 나중에 처리된 관측으로 갱신한다")
    void sameTimestampTakesTheLaterWrite() throws Exception {
        ingest(Map.of("peopleCount", 10, "observedAt", OBSERVED_AT));
        ingest(Map.of("peopleCount", 20, "observedAt", OBSERVED_AT));

        // 어느 쪽이 옳은지 판단할 근거가 없다. 재처리로 보낸 수정값이 조용히
        // 버려지지 않는 쪽을 고른다.
        assertThat(statusRepository.findBySiteId(siteId()).orElseThrow().getPeopleCount())
                .isEqualTo(20);
    }

    // ---------- 검증 ----------

    @Test
    @DisplayName("없는 식당은 422다")
    void unknownSiteIsRejected() throws Exception {
        MvcResult result = ingest(Map.of("siteId", 999999L));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("UNKNOWN_DINING_SITE");
    }

    @Test
    @DisplayName("음수 인원은 400이다")
    void negativePeopleCountIsRejected() throws Exception {
        assertThat(ingest(Map.of("peopleCount", -1)).getResponse().getStatus()).isEqualTo(400);
        assertThat(observationRepository.count()).isZero();
    }

    @Test
    @DisplayName("0명은 관측일 수 있다")
    void zeroPeopleIsAllowed() throws Exception {
        assertThat(ingest(Map.of("peopleCount", 0)).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("인원에 상한을 두지 않는다")
    void noArbitraryUpperLimit() throws Exception {
        assertThat(ingest(Map.of("peopleCount", 100000)).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("모르는 단계는 400이다")
    void unknownLevelIsRejected() throws Exception {
        assertThat(ingest(Map.of("level", "PRETTY_BUSY")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("네 단계를 그대로 받는다")
    void allKnownLevelsAreAccepted() throws Exception {
        for (String level : List.of("COMFORTABLE", "NORMAL", "CROWDED", "VERY_CROWDED")) {
            assertThat(ingest(Map.of("level", level)).getResponse().getStatus())
                    .as(level)
                    .isEqualTo(202);
        }
    }

    @Test
    @DisplayName("필수 값이 빠지면 400이다")
    void missingFieldsAreRejected() throws Exception {
        for (String field : List.of("siteId", "deviceId", "peopleCount", "level", "observedAt")) {
            Map<String, Object> body = payload(Map.of());
            body.remove(field);

            assertThat(ingest(body, SERVICE_TOKEN).getResponse().getStatus())
                    .as(field)
                    .isEqualTo(400);
        }
    }

    @Test
    @DisplayName("빈 장비 식별자는 400이다")
    void blankDeviceIdIsRejected() throws Exception {
        assertThat(ingest(Map.of("deviceId", "  ")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("보냈으면서 빈 모델 버전은 거절한다")
    void blankModelVersionIsRejected() throws Exception {
        // 빈 문자열을 버전으로 저장하면 나중에 "버전 없음"과 구분되지 않는다.
        MvcResult result = ingest(Map.of("modelVersion", "   "));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(observationRepository.count()).isZero();
    }

    // ---------- 다른 경로와의 정합 ----------

    @Test
    @DisplayName("시계열이 수집된 관측을 본다")
    void timeseriesSeesTheIngestedObservation() throws Exception {
        ingest(Map.of("peopleCount", 55));

        JsonNode data = body(getJson(
                        "/api/v1/admin/congestion/timeseries?siteId=" + siteId()
                                + "&from=2026-09-15T00:00:00Z&to=2026-09-15T23:59:59Z",
                        adminToken()))
                .path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("peopleCount").asInt()).isEqualTo(55);
        assertThat(data.get(0).path("source").asText()).isEqualTo(CongestionIngestService.SOURCE);
    }

    @Test
    @DisplayName("공개 현재 혼잡도가 가장 최근 관측을 본다")
    void publicCurrentSeesTheNewestObservation() throws Exception {
        ingest(Map.of("peopleCount", 10, "observedAt", "2026-09-15T02:00:00Z"));
        ingest(Map.of("peopleCount", 88, "observedAt", "2026-09-15T02:30:00Z", "level", "CROWDED"));

        JsonNode data =
                body(getJson("/api/v1/congestion/current?siteId=" + siteId())).path("data");

        // 이력과 현재가 갈라지면 사용자가 보는 값과 관리자가 보는 값이 달라진다.
        assertThat(data.path("peopleCount").asInt()).isEqualTo(88);
        assertThat(data.path("level").asText()).isEqualTo("CROWDED");
    }

    @Test
    @DisplayName("영상 필드를 받지 않는다")
    void rawFrameFieldsAreNotAccepted() throws Exception {
        // 원본 프레임이 서비스 백엔드에 쌓이기 시작하면 그 순간부터 다른
        // 종류의 시스템이 된다.
        ingest(Map.of("image", "data:image/jpeg;base64,AAAA", "frame", "AAAA"));

        String stored = objectMapper.writeValueAsString(onlyObservation().getDeviceId());
        assertThat(stored).doesNotContain("base64");
    }

    @Test
    @DisplayName("수집 요청이 계약에 없는 값을 저장하지 않는다")
    void unknownFieldsAreNotPersisted() throws Exception {
        ingest(Map.of("userId", 123, "phone", "01012345678"));

        CongestionObservation stored = onlyObservation();
        assertThat(objectMapper.writeValueAsString(stored.getDeviceId()))
                .doesNotContain("01012345678");
        assertThat(stored.getDisplayMessage()).isNull();
    }
}

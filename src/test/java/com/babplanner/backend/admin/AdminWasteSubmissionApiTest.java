package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.babplanner.backend.admin.service.AdminWasteSubmissionService;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.reward.domain.RewardTransaction;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** 잔반 인증 수동 검토: 제출 → PENDING → 관리자 승인/반려 → 포인트. */
class AdminWasteSubmissionApiTest extends ApiIntegrationTest {

    private static final String USER_PATH = "/api/v1/waste-submissions";
    private static final String ADMIN_PATH = "/api/v1/admin/waste-submissions";
    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5
    };

    @Autowired private MenuRepository menuRepository;
    @Autowired private WasteImageStorage imageStorage;
    @Autowired private AdminWasteSubmissionService reviewService;

    private long submit(JsonNode auth) throws Exception {
        return submit(auth, JPEG);
    }

    private long submit(JsonNode auth, byte[] bytes) throws Exception {
        return body(perform(multipart(USER_PATH)
                        .file(new MockMultipartFile("image", "lunch.jpg", "image/jpeg", bytes))
                        .param("menuId", String.valueOf(menuRepository.findAll().getFirst().getId()))
                        .param("capturedAt", "2026-08-26T03:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth))))
                .path("data")
                .path("id")
                .asLong();
    }

    private MvcResult review(String action, long id, JsonNode admin) throws Exception {
        return perform(post(ADMIN_PATH + "/" + id + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(admin)));
    }

    private JsonNode userView(long id, JsonNode user) throws Exception {
        return body(getJson(USER_PATH + "/" + id, accessTokenOf(user))).path("data");
    }

    private int balanceOf(long userId) {
        return rewardAccountRepository.findByUserId(userId).orElseThrow().getPoints();
    }

    private long userId(JsonNode auth) {
        return auth.path("user").path("id").asLong();
    }

    @Test
    @DisplayName("제출하면 PENDING이고 포인트는 없다")
    void submitIsPending() throws Exception {
        JsonNode user = signup();

        long id = submit(user);

        JsonNode data = userView(id, user);
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
        assertThat(data.path("earnedPoints").isNull()).isTrue();
        assertThat(data.path("couponIssued").isNull()).isTrue();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("승인하면 100포인트가 한 번 적립되고 요약과 내역과 사용자 화면에 반영된다")
    void approveCreditsOnce() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);

        MvcResult result = review("approve", id, admin);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode item = body(result).path("data");
        assertThat(item.path("status").asText()).isEqualTo("APPROVED");
        assertThat(item.path("earnedPoints").asInt()).isEqualTo(100);
        assertThat(item.path("couponIssued").asBoolean()).isFalse();
        assertThat(item.path("reviewedAt").isNull()).isFalse();
        WasteSubmission stored = wasteSubmissionRepository.findById(id).orElseThrow();
        assertThat(stored.getReviewedByUserId()).isEqualTo(userId(admin));

        assertThat(body(getJson("/api/v1/rewards/summary", accessTokenOf(user)))
                        .path("data")
                        .path("points")
                        .asInt())
                .isEqualTo(100);
        JsonNode transactions =
                body(getJson("/api/v1/rewards/transactions", accessTokenOf(user))).path("data");
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).path("amount").asInt()).isEqualTo(100);
        assertThat(transactions.get(0).path("type").asText()).isEqualTo("WASTE_APPROVED");
        JsonNode view = userView(id, user);
        assertThat(view.path("status").asText()).isEqualTo("APPROVED");
        assertThat(view.path("earnedPoints").asInt()).isEqualTo(100);
        assertThat(view.path("couponIssued").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("승인을 다시 해도 추가 지급이 없다")
    void reapproveDoesNotCreditAgain() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);

        review("approve", id, admin);
        MvcResult second = review("approve", id, admin);
        MvcResult third = review("approve", id, admin);

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(third.getResponse().getStatus()).isEqualTo(200);
        assertThat(rewardTransactionRepository.findAll()).hasSize(1);
        assertThat(balanceOf(userId(user))).isEqualTo(100);
    }

    @Test
    @DisplayName("두 관리자가 동시에 승인해도 한 번만 지급된다")
    void concurrentApproveCreditsOnce() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);
        long adminId = userId(admin);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    reviewService.approve(id, adminId);
                } catch (RuntimeException loser) {
                    // 동시 충돌로 롤백된 쪽. 이미 한쪽이 커밋했다.
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<RewardTransaction> ledger = rewardTransactionRepository.findAll();
        assertThat(ledger).hasSize(1);
        assertThat(balanceOf(userId(user))).isEqualTo(100);
        assertThat(rewardAccountRepository.findAll()).hasSize(1);
        WasteSubmission stored = wasteSubmissionRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(stored.getEarnedPoints()).isEqualTo(100);
    }

    @Test
    @DisplayName("반려하면 REJECTED이고 포인트가 없다")
    void rejectGivesNothing() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);

        JsonNode item = body(review("reject", id, admin)).path("data");

        assertThat(item.path("status").asText()).isEqualTo("REJECTED");
        assertThat(item.path("earnedPoints").asInt()).isZero();
        assertThat(item.path("couponIssued").asBoolean()).isFalse();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
        JsonNode view = userView(id, user);
        assertThat(view.path("status").asText()).isEqualTo("REJECTED");
        assertThat(view.path("earnedPoints").asInt()).isZero();
        assertThat(view.path("earnedPoints").isNull()).isFalse();
        assertThat(view.path("couponIssued").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("반려된 건도 나중에 승인하면 한 번 지급된다")
    void rejectedThenApproved() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);
        review("reject", id, admin);
        // 반려 재호출은 멱등이다.
        assertThat(review("reject", id, admin).getResponse().getStatus()).isEqualTo(200);

        MvcResult approved = review("approve", id, admin);
        review("approve", id, admin);

        assertThat(body(approved).path("data").path("earnedPoints").asInt()).isEqualTo(100);
        assertThat(rewardTransactionRepository.findAll()).hasSize(1);
        assertThat(balanceOf(userId(user))).isEqualTo(100);
    }

    @Test
    @DisplayName("승인된 건은 반려할 수 없고 포인트는 그대로다")
    void rejectAfterApprovalConflicts() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long id = submit(user);
        review("approve", id, admin);

        MvcResult result = review("reject", id, admin);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("WASTE_SUBMISSION_ALREADY_APPROVED");
        assertThat(userView(id, user).path("status").asText()).isEqualTo("APPROVED");
        assertThat(balanceOf(userId(user))).isEqualTo(100);
    }

    @Test
    @DisplayName("없는 제출은 404다")
    void unknownIdIsNotFound() throws Exception {
        JsonNode admin = adminAuth();

        assertThat(review("approve", 999999, admin).getResponse().getStatus()).isEqualTo(404);
        assertThat(review("reject", 999999, admin).getResponse().getStatus()).isEqualTo(404);
        assertThat(getJson(ADMIN_PATH + "/999999/image", accessTokenOf(admin))
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("일반 사용자는 모든 관리자 엔드포인트에 403, 익명은 401이다")
    void authorization() throws Exception {
        JsonNode user = signup();
        long id = submit(user);
        String token = accessTokenOf(user);

        assertThat(getJson(ADMIN_PATH, token).getResponse().getStatus()).isEqualTo(403);
        assertThat(getJson(ADMIN_PATH + "/" + id + "/image", token).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(perform(post(ADMIN_PATH + "/" + id + "/approve")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(perform(post(ADMIN_PATH + "/" + id + "/reject")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);

        assertThat(perform(get(ADMIN_PATH)).getResponse().getStatus()).isEqualTo(401);
        assertThat(perform(get(ADMIN_PATH + "/" + id + "/image")).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(perform(post(ADMIN_PATH + "/" + id + "/approve")).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(perform(post(ADMIN_PATH + "/" + id + "/reject")).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(wasteSubmissionRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(WasteSubmissionStatus.PENDING);
    }

    @Test
    @DisplayName("목록은 최신순이고 전화번호를 마스킹하며 상태와 개수를 거를 수 있다")
    void listItems() throws Exception {
        JsonNode user = signup();
        JsonNode admin = adminAuth();
        long first = submit(user);
        clock.advance(java.time.Duration.ofMinutes(1));
        long second = submit(user);
        review("approve", first, admin);
        String token = accessTokenOf(admin);

        JsonNode all = body(getJson(ADMIN_PATH, token)).path("data");
        JsonNode pending = body(getJson(ADMIN_PATH + "?status=PENDING", token)).path("data");
        JsonNode approved = body(getJson(ADMIN_PATH + "?status=APPROVED", token)).path("data");
        JsonNode limited = body(getJson(ADMIN_PATH + "?limit=1", token)).path("data");

        assertThat(all).hasSize(2);
        assertThat(all.get(0).path("id").asLong()).isEqualTo(second);
        assertThat(all.get(1).path("id").asLong()).isEqualTo(first);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).path("id").asLong()).isEqualTo(second);
        assertThat(approved).hasSize(1);
        assertThat(limited).hasSize(1);
        JsonNode item = approved.get(0);
        assertThat(item.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id", "userName", "userPhoneMasked", "menuId", "menuName", "serviceDate",
                        "status", "earnedPoints", "couponIssued", "createdAt", "capturedAt",
                        "reviewedAt");
        assertThat(item.path("userPhoneMasked").asText()).isEqualTo("010-****-5678");
        assertThat(item.path("userName").asText()).isEqualTo("홍길동");
        assertThat(item.path("menuName").asText()).isNotBlank();
        assertThat(item.path("serviceDate").asText()).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        assertThat(pending.get(0).path("earnedPoints").isNull()).isTrue();
        assertThat(pending.get(0).path("couponIssued").isNull()).isTrue();
        assertThat(pending.get(0).path("reviewedAt").isNull()).isTrue();
        assertThat(getJson(ADMIN_PATH, token).getResponse().getContentAsString())
                .doesNotContain(PHONE);
    }

    @Test
    @DisplayName("limit은 1~200이다")
    void limitBounds() throws Exception {
        String token = adminToken();

        assertThat(getJson(ADMIN_PATH + "?limit=0", token).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(getJson(ADMIN_PATH + "?limit=201", token).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(getJson(ADMIN_PATH + "?limit=200", token).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("이미지는 바이트 그대로, 판별한 Content-Type, no-store로 내려간다")
    void imageIsServed() throws Exception {
        JsonNode user = signup();
        long id = submit(user);
        String token = adminToken();

        MvcResult result = getJson(ADMIN_PATH + "/" + id + "/image", token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(JPEG);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(result.getResponse().getHeader("Cache-Control"))
                .contains("no-store")
                .contains("private");
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 내보내지 않는다")
    void nonImageBytesAreRejected() throws Exception {
        JsonNode user = signup();
        long id = submit(user, "<html>not an image</html>".getBytes(StandardCharsets.UTF_8));

        MvcResult result = getJson(ADMIN_PATH + "/" + id + "/image", adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentType()).doesNotStartWith("image/");
    }

    @Test
    @DisplayName("저장소에 파일이 없으면 404다")
    void missingImageIsNotFound() throws Exception {
        JsonNode user = signup();
        long id = submit(user);
        imageStorage.delete(wasteSubmissionRepository.findById(id).orElseThrow().getImageStorageKey());

        MvcResult result = getJson(ADMIN_PATH + "/" + id + "/image", adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}

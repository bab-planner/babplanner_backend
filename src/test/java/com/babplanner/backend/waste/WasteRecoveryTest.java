package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.service.WasteRecoveryRunner;
import com.babplanner.backend.waste.service.WasteSubmissionStateStore;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재시작 복구.
 *
 * <p>비동기 처리 중 서버가 내려가 진행 중 상태로 남은 건을 이어받는지 본다.
 * 서버를 실제로 껐다 켜는 대신, 그 상태로 저장된 행을 만들어 두고 복구를
 * 직접 실행한다.
 */
class WasteRecoveryTest extends ApiIntegrationTest {

    private static final byte[] IMAGE_BYTES = "fake-image".getBytes(StandardCharsets.UTF_8);

    @Autowired private WasteRecoveryRunner recoveryRunner;
    @Autowired private WasteSubmissionStateStore stateStore;
    @Autowired private WasteImageStorage imageStorage;
    @Autowired private MenuRepository menuRepository;

    private String storedImageKey() {
        return imageStorage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE_BYTES),
                        "a.jpg",
                        "image/jpeg",
                        IMAGE_BYTES.length))
                .storageKey();
    }

    /** 처리 도중 멈춘 것처럼 보이는 행을 만든다. */
    private WasteSubmission stuckAt(WasteSubmissionStatus status, String storageKey)
            throws Exception {
        JsonNode auth = signup();
        long userId = auth.path("user").path("id").asLong();
        long menuId = menuRepository.findAll().getFirst().getId();
        Instant now = clock.instant();
        WasteSubmission submission = wasteSubmissionRepository.save(
                new WasteSubmission(userId, menuId, storageKey, now, now));
        if (status != WasteSubmissionStatus.RECEIVED) {
            stateStore.claim(submission.getId(), WasteSubmissionStatus.RECEIVED,
                    WasteSubmissionStatus.VERIFYING);
            if (status == WasteSubmissionStatus.ANALYZING) {
                stateStore.claim(submission.getId(), WasteSubmissionStatus.VERIFYING,
                        WasteSubmissionStatus.ANALYZING);
            }
        }
        return wasteSubmissionRepository.findById(submission.getId()).orElseThrow();
    }

    private WasteSubmissionStatus statusOf(Long id) {
        return wasteSubmissionRepository.findById(id).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("RECEIVED로 멈춘 건을 이어받는다")
    void resumesReceived() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.RECEIVED, storedImageKey());

        assertThat(recoveryRunner.recover()).isEqualTo(1);

        assertThat(statusOf(stuck.getId())).isEqualTo(WasteSubmissionStatus.APPROVED);
    }

    @Test
    @DisplayName("VERIFYING으로 멈춘 건을 이어받는다")
    void resumesVerifying() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.VERIFYING, storedImageKey());
        assertThat(stuck.getStatus()).isEqualTo(WasteSubmissionStatus.VERIFYING);

        recoveryRunner.recover();

        assertThat(statusOf(stuck.getId())).isEqualTo(WasteSubmissionStatus.APPROVED);
    }

    @Test
    @DisplayName("ANALYZING으로 멈춘 건을 이어받는다")
    void resumesAnalyzing() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.ANALYZING, storedImageKey());
        assertThat(stuck.getStatus()).isEqualTo(WasteSubmissionStatus.ANALYZING);

        recoveryRunner.recover();

        assertThat(statusOf(stuck.getId())).isEqualTo(WasteSubmissionStatus.APPROVED);
    }

    @Test
    @DisplayName("이미지가 사라진 건은 FAILED로 끝낸다")
    void missingImageFails() throws Exception {
        WasteSubmission stuck =
                stuckAt(WasteSubmissionStatus.RECEIVED, "00000000000000000000000000000000");

        recoveryRunner.recover();

        assertThat(statusOf(stuck.getId())).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("이미 끝난 건은 건드리지 않는다")
    void terminalIsUntouched() throws Exception {
        WasteSubmission done = stuckAt(WasteSubmissionStatus.RECEIVED, storedImageKey());
        recoveryRunner.recover();
        Instant updatedAt =
                wasteSubmissionRepository.findById(done.getId()).orElseThrow().getUpdatedAt();

        assertThat(recoveryRunner.recover()).isZero();

        WasteSubmission after = wasteSubmissionRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("선점은 한 번만 성공한다")
    void claimIsAtomic() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.RECEIVED, storedImageKey());

        boolean first = stateStore.claim(
                stuck.getId(), WasteSubmissionStatus.RECEIVED, WasteSubmissionStatus.VERIFYING);
        boolean second = stateStore.claim(
                stuck.getId(), WasteSubmissionStatus.RECEIVED, WasteSubmissionStatus.VERIFYING);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("처리 시작 시각을 남긴다")
    void recordsProcessingStart() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.RECEIVED, storedImageKey());

        recoveryRunner.recover();

        assertThat(wasteSubmissionRepository
                        .findById(stuck.getId())
                        .orElseThrow()
                        .getProcessingStartedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("복구가 리워드를 지급하지 않는다")
    void recoveryGrantsNoReward() throws Exception {
        WasteSubmission stuck = stuckAt(WasteSubmissionStatus.RECEIVED, storedImageKey());

        recoveryRunner.recover();

        WasteSubmission after = wasteSubmissionRepository.findById(stuck.getId()).orElseThrow();
        assertThat(after.getEarnedPoints()).isNull();
        assertThat(after.getCouponIssued()).isNull();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
        assertThat(couponRepository.findAll()).isEmpty();
    }
}

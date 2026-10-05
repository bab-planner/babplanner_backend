package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.AdminWasteSubmissionResponse;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.reward.config.RewardProperties;
import com.babplanner.backend.reward.service.RewardService;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.repository.AdminWasteSubmissionRow;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 잔반 인증 수동 검토.
 *
 * <p>승인은 한 번만 포인트를 지급한다. 상태 전이는 조건부 UPDATE로 원자적이고, 지급은
 * 같은 트랜잭션 안에서 제출 id를 출처로 하는 {@link RewardService#creditOnce}로 한다.
 * 두 관리자가 동시에 승인해도 한쪽만 전이에 성공하고, 원장의 unique 제약이 마지막
 * 방어선이다. APPROVED는 종료 상태이며 반려할 수 없다(포인트 회수 없음).
 */
@Service
public class AdminWasteSubmissionService {

    static final String REFERENCE_TYPE = "WASTE_SUBMISSION";
    static final String REWARD_TYPE = "WASTE_APPROVED";
    static final String REWARD_DESCRIPTION = "잔반 인증 적립";

    private static final List<WasteSubmissionStatus> FINAL_FOR_REJECT =
            List.of(WasteSubmissionStatus.APPROVED, WasteSubmissionStatus.REJECTED);

    /** 응답으로 내보내는 이미지. */
    public record StoredImage(byte[] bytes, String contentType) {}

    private final WasteSubmissionRepository repository;
    private final RewardService rewardService;
    private final RewardProperties rewardProperties;
    private final WasteImageStorage imageStorage;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AdminWasteSubmissionService(
            WasteSubmissionRepository repository,
            RewardService rewardService,
            RewardProperties rewardProperties,
            WasteImageStorage imageStorage,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rewardService = rewardService;
        this.rewardProperties = rewardProperties;
        this.imageStorage = imageStorage;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<AdminWasteSubmissionResponse> list(WasteSubmissionStatus status, int limit) {
        if (limit < 1 || limit > 200) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        PageRequest page = PageRequest.of(0, limit);
        List<AdminWasteSubmissionRow> rows = status == null
                ? repository.findRows(page)
                : repository.findRowsByStatus(status, page);
        return rows.stream().map(AdminWasteSubmissionResponse::from).toList();
    }

    public AdminWasteSubmissionResponse approve(Long id, Long adminId) {
        WasteSubmission submission = find(id);
        if (submission.getStatus() == WasteSubmissionStatus.APPROVED) {
            // 이미 승인됨. 멱등이며 추가 지급은 없다.
            return row(id);
        }
        // 계정 행은 전이 트랜잭션 밖에서 확보한다(동시 생성 충돌이 전이를 망치지 않게).
        rewardService.ensureAccount(submission.getUserId());
        try {
            transaction.executeWithoutResult(
                    status -> doApprove(submission.getUserId(), id, adminId));
        } catch (DataIntegrityViolationException concurrentApproval) {
            // 동시에 승인한 쪽이 지급까지 커밋했다. 이쪽은 전부 롤백되었다.
        }
        return row(id);
    }

    private void doApprove(Long userId, Long id, Long adminId) {
        Instant now = clock.instant();
        int points = rewardProperties.getWasteApprovalPoints();
        int moved = repository.approveIfNotApproved(
                id, WasteSubmissionStatus.APPROVED, points, adminId, now);
        if (moved != 1) {
            // 다른 관리자가 먼저 승인했다.
            return;
        }
        int credited = rewardService.creditOnce(
                userId, points, REWARD_TYPE, REWARD_DESCRIPTION, REFERENCE_TYPE, id);
        if (credited != points) {
            // 이미 같은 출처의 지급 기록이 있으면 그 금액이 실제 지급액이다.
            repository
                    .findById(id)
                    .ifPresent(w -> w.markReviewed(
                            WasteSubmissionStatus.APPROVED, credited, adminId, now));
        }
    }

    @Transactional
    public AdminWasteSubmissionResponse reject(Long id, Long adminId) {
        find(id);
        int moved = repository.rejectIfOpen(
                id, WasteSubmissionStatus.REJECTED, FINAL_FOR_REJECT, adminId, clock.instant());
        if (moved != 1) {
            WasteSubmission current = find(id);
            if (current.getStatus() == WasteSubmissionStatus.APPROVED) {
                throw new ApiException(ErrorCode.WASTE_SUBMISSION_ALREADY_APPROVED);
            }
            // 이미 REJECTED. 멱등.
        }
        return row(id);
    }

    @Transactional(readOnly = true)
    public StoredImage image(Long id) {
        WasteSubmission submission = find(id);
        byte[] bytes;
        try {
            if (!imageStorage.exists(submission.getImageStorageKey())) {
                throw new ApiException(ErrorCode.WASTE_SUBMISSION_NOT_FOUND);
            }
            try (InputStream input = imageStorage.read(submission.getImageStorageKey())) {
                bytes = input.readAllBytes();
            }
        } catch (WasteImageStorageException | IOException exception) {
            throw new ApiException(ErrorCode.WASTE_SUBMISSION_NOT_FOUND);
        }
        String contentType = detectImageType(bytes);
        if (contentType == null) {
            throw new ApiException(ErrorCode.INVALID_WASTE_IMAGE);
        }
        return new StoredImage(bytes, contentType);
    }

    private WasteSubmission find(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.WASTE_SUBMISSION_NOT_FOUND));
    }

    private AdminWasteSubmissionResponse row(Long id) {
        return repository
                .findRowById(id)
                .map(AdminWasteSubmissionResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.WASTE_SUBMISSION_NOT_FOUND));
    }

    /** 확장자나 저장된 content-type이 아니라 실제 바이트로 판별한다. 이미지가 아니면 null. */
    static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.equals("heic") || brand.equals("heix") || brand.equals("mif1")) {
                return "image/heic";
            }
        }
        return null;
    }
}

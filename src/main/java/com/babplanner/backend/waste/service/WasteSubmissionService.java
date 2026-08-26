package com.babplanner.backend.waste.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.waste.config.WasteProperties;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.dto.WasteSubmissionResponse;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import com.babplanner.backend.waste.storage.StoredWasteImage;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 잔반 사진 제출 접수와 조회. 실제 분석은 커밋 이후 비동기로 진행한다. */
@Service
public class WasteSubmissionService {

    private final WasteSubmissionRepository submissionRepository;
    private final MenuRepository menuRepository;
    private final WasteImageStorage imageStorage;
    private final WasteProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public WasteSubmissionService(
            WasteSubmissionRepository submissionRepository,
            MenuRepository menuRepository,
            WasteImageStorage imageStorage,
            WasteProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.submissionRepository = submissionRepository;
        this.menuRepository = menuRepository;
        this.imageStorage = imageStorage;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 제출을 접수한다.
     *
     * <p>verificationToken은 받되 저장하지 않고 기록하지도 않는다. 현장 인증
     * 방식이 아직 정해지지 않아 "검증 성공" 같은 의미를 부여할 근거가 없다.
     */
    @Transactional
    public WasteSubmissionResponse create(
            Long userId, Long menuId, MultipartFile image, Instant capturedAt) {
        if (menuId == null || !menuRepository.existsById(menuId)) {
            throw new ApiException(ErrorCode.INVALID_WASTE_SUBMISSION);
        }
        if (capturedAt == null) {
            throw new ApiException(ErrorCode.INVALID_WASTE_SUBMISSION);
        }
        validateImage(image);

        StoredWasteImage stored = storeImage(image);
        WasteSubmission submission;
        try {
            submission = submissionRepository.save(new WasteSubmission(
                    userId, menuId, stored.storageKey(), capturedAt, clock.instant()));
        } catch (RuntimeException exception) {
            // 행이 남지 않았으면 이미지도 남기지 않는다.
            imageStorage.delete(stored.storageKey());
            throw exception;
        }

        // 커밋된 뒤에만 분석이 시작되도록 이벤트로 넘긴다. 여기서 직접
        // 스레드를 만들지 않는다.
        eventPublisher.publishEvent(new WasteSubmissionCreatedEvent(submission.getId()));
        return WasteSubmissionResponse.from(submission);
    }

    /** 본인 제출만 조회한다. 남의 것과 없는 것은 같은 응답이라 존재를 알 수 없다. */
    @Transactional(readOnly = true)
    public WasteSubmissionResponse get(Long userId, Long submissionId) {
        return submissionRepository
                .findByIdAndUserId(submissionId, userId)
                .map(WasteSubmissionResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.WASTE_SUBMISSION_NOT_FOUND));
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_WASTE_IMAGE);
        }
        if (image.getSize() > properties.getMaxImageBytes()) {
            throw new ApiException(ErrorCode.INVALID_WASTE_IMAGE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ApiException(ErrorCode.INVALID_WASTE_IMAGE);
        }
    }

    private StoredWasteImage storeImage(MultipartFile image) {
        return imageStorage.store(new WasteImageUpload(
                image::getInputStream,
                image.getOriginalFilename(),
                image.getContentType(),
                image.getSize()));
    }
}

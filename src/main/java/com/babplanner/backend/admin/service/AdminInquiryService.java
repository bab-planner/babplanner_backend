package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.InquiryAdminUpdateRequest;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.inquiry.domain.Inquiry;
import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.babplanner.backend.inquiry.dto.InquiryResponse;
import com.babplanner.backend.inquiry.repository.InquiryRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 문의 관리.
 *
 * <p>사용자 쪽 조회는 본인 것만 볼 수 있지만 여기는 모두 본다. 소유권 대신
 * 역할이 접근을 정한다.
 */
@Service
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final Clock clock;

    public AdminInquiryService(InquiryRepository inquiryRepository, Clock clock) {
        this.inquiryRepository = inquiryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InquiryResponse> getInquiries(InquiryStatus status) {
        List<Inquiry> inquiries = status == null
                ? inquiryRepository.findAllByOrderByCreatedAtDescIdDesc()
                : inquiryRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status);
        return inquiries.stream().map(InquiryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InquiryResponse getInquiry(Long inquiryId) {
        return InquiryResponse.from(find(inquiryId));
    }

    /**
     * 상태와 답변을 고친다.
     *
     * <p>보낸 것만 바꾼다. 상태만 보낸 요청이 답변을 지우거나, 답변만 보낸
     * 요청이 상태를 바꾸면 관리자가 의도하지 않은 변경이 일어난다.
     */
    @Transactional
    public InquiryResponse update(Long inquiryId, InquiryAdminUpdateRequest request) {
        if (request.isEmpty()) {
            // 아무것도 바꾸지 않는 요청은 성공했다고 말할 것이 없다.
            throw new ApiException(ErrorCode.EMPTY_UPDATE);
        }
        if (request.hasInvalidAnswer()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        Inquiry inquiry = find(inquiryId);

        if (request.status() != null) {
            inquiry.changeStatus(request.status(), clock.instant());
        }
        if (request.touchesAnswer()) {
            // JSON null은 "지워 달라"는 뜻이다. 필드를 안 보낸 것과 다르다.
            inquiry.applyAnswer(request.answerOrNull(), clock.instant());
        }

        // 답변을 썼다고 상태를 ANSWERED로 바꾸지 않는다. 상태는 관리자가
        // 명시적으로 정하는 값이고, 답변을 적어 두고 아직 검토 중일 수 있다.
        return InquiryResponse.from(inquiry);
    }

    private Inquiry find(Long inquiryId) {
        return inquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new ApiException(ErrorCode.INQUIRY_NOT_FOUND));
    }
}

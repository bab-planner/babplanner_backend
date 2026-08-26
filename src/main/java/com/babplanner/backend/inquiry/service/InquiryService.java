package com.babplanner.backend.inquiry.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.inquiry.domain.Inquiry;
import com.babplanner.backend.inquiry.dto.InquiryCreateRequest;
import com.babplanner.backend.inquiry.dto.InquiryResponse;
import com.babplanner.backend.inquiry.repository.InquiryRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문의/건의. 항상 인증된 사용자 본인의 데이터만 다룬다. */
@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final Clock clock;

    public InquiryService(InquiryRepository inquiryRepository, Clock clock) {
        this.inquiryRepository = inquiryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InquiryResponse> getMyInquiries(Long userId) {
        return inquiryRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(InquiryResponse::from)
                .toList();
    }

    /** 남의 문의와 없는 문의를 같은 404로 응답해 존재 여부를 노출하지 않는다. */
    @Transactional(readOnly = true)
    public InquiryResponse getInquiry(Long userId, Long inquiryId) {
        return inquiryRepository
                .findByIdAndUserId(inquiryId, userId)
                .map(InquiryResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    @Transactional
    public InquiryResponse createInquiry(Long userId, InquiryCreateRequest request) {
        Inquiry inquiry = inquiryRepository.save(new Inquiry(
                userId, request.category(), request.title(), request.content(), clock.instant()));
        return InquiryResponse.from(inquiry);
    }
}

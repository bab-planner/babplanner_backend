package com.babplanner.backend.notice.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.notice.dto.NoticeResponse;
import com.babplanner.backend.notice.repository.NoticeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 조회. 정렬 책임은 서버에 있다. */
@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    /** 중요 공지 먼저, 그다음 최신순. 앱은 이 순서를 그대로 그린다. */
    @Transactional(readOnly = true)
    public List<NoticeResponse> getNotices() {
        return noticeRepository.findAllByPublishedTrueOrderByImportantDescPublishedAtDescIdDesc().stream()
                .map(NoticeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(Long noticeId) {
        return noticeRepository
                .findByIdAndPublishedTrue(noticeId)
                .map(NoticeResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
    }
}

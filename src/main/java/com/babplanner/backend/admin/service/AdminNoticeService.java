package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.NoticeWriteRequest;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.notice.domain.Notice;
import com.babplanner.backend.notice.dto.NoticeResponse;
import com.babplanner.backend.notice.repository.NoticeRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공지 관리.
 *
 * <p>사용자가 보는 것과 같은 Notice를 쓴다. 다른 점은 공개되지 않은 것까지
 * 본다는 것뿐이다.
 */
@Service
public class AdminNoticeService {

    private final NoticeRepository noticeRepository;
    private final Clock clock;

    public AdminNoticeService(NoticeRepository noticeRepository, Clock clock) {
        this.noticeRepository = noticeRepository;
        this.clock = clock;
    }

    /** 작성 중인 공지까지 모두. 관리자는 그것도 관리해야 한다. */
    @Transactional(readOnly = true)
    public List<NoticeResponse> getNotices() {
        return noticeRepository.findAllByOrderByImportantDescPublishedAtDescIdDesc().stream()
                .map(NoticeResponse::from)
                .toList();
    }

    @Transactional
    public NoticeResponse create(NoticeWriteRequest request) {
        Notice notice = new Notice(
                request.title(),
                request.content(),
                request.importantOrDefault(),
                request.publishedOrDefault(),
                // 게시 시각은 만든 시각이다. 공개를 나중에 켜도 이 값은 그대로다.
                clock.instant(),
                clock.instant());
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponse update(Long noticeId, NoticeWriteRequest request) {
        Notice notice = noticeRepository
                .findById(noticeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));

        notice.applyWrite(
                request.title(),
                request.content(),
                request.importantOrDefault(),
                request.publishedOrDefault(),
                clock.instant());
        return NoticeResponse.from(notice);
    }

    @Transactional
    public void delete(Long noticeId) {
        Notice notice = noticeRepository
                .findById(noticeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
        noticeRepository.delete(notice);
    }
}

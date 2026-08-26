package com.babplanner.backend.notice.repository;

import com.babplanner.backend.notice.domain.Notice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 중요 공지 먼저, 그다음 최신순.
     *
     * <p>앱이 응답 순서를 그대로 렌더링하므로 정렬 책임은 서버에 있다.
     * 같은 시각이면 id 역순으로 잘라 순서가 흔들리지 않게 한다.
     */
    /** 관리자용. 작성 중인 공지까지 모두 본다. */
    List<Notice> findAllByOrderByImportantDescPublishedAtDescIdDesc();

    /** 사용자용. 공개된 것만 나간다. */
    List<Notice> findAllByPublishedTrueOrderByImportantDescPublishedAtDescIdDesc();

    /** 사용자용 상세. 공개되지 않은 공지는 없는 것과 같다. */
    Optional<Notice> findByIdAndPublishedTrue(Long id);
}

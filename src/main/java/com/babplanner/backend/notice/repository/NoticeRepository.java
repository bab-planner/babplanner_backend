package com.babplanner.backend.notice.repository;

import com.babplanner.backend.notice.domain.Notice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 중요 공지 먼저, 그다음 최신순.
     *
     * <p>앱이 응답 순서를 그대로 렌더링하므로 정렬 책임은 서버에 있다.
     * 같은 시각이면 id 역순으로 잘라 순서가 흔들리지 않게 한다.
     */
    List<Notice> findAllByOrderByImportantDescPublishedAtDescIdDesc();
}

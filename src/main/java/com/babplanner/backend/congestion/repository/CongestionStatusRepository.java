package com.babplanner.backend.congestion.repository;

import com.babplanner.backend.congestion.domain.CongestionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionStatusRepository extends JpaRepository<CongestionStatus, Long> {

    @EntityGraph(attributePaths = {"site"})
    Optional<CongestionStatus> findBySiteId(Long siteId);

    /** siteId를 지정하지 않았을 때 쓰는 대표 사이트(노출 순서가 가장 앞선 곳). */
    @EntityGraph(attributePaths = {"site"})
    Optional<CongestionStatus> findFirstByOrderBySiteSortOrderAscSiteIdAsc();
}

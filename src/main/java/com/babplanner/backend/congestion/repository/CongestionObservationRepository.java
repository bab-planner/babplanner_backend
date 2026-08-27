package com.babplanner.backend.congestion.repository;

import com.babplanner.backend.congestion.domain.CongestionObservation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionObservationRepository extends JpaRepository<CongestionObservation, Long> {

    /**
     * 한 사이트의 기간 내 관측을 시간 순으로.
     *
     * <p>같은 시각에 두 건이 들어와도 순서가 흔들리지 않도록 id까지 함께
     * 정렬한다. 양 끝은 포함이다.
     */
    @EntityGraph(attributePaths = {"site"})
    List<CongestionObservation> findBySiteIdAndObservedAtBetweenOrderByObservedAtAscIdAsc(
            Long siteId, Instant from, Instant to);
}

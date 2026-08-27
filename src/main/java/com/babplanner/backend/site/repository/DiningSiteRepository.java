package com.babplanner.backend.site.repository;

import com.babplanner.backend.site.domain.DiningSite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningSiteRepository extends JpaRepository<DiningSite, Long> {

    Optional<DiningSite> findByCode(String code);

    List<DiningSite> findAllByOrderBySortOrderAscIdAsc();

    /** 대표 식당. siteId를 주지 않은 조회가 어디를 볼지 정한다. */
    Optional<DiningSite> findFirstByOrderBySortOrderAscIdAsc();
}

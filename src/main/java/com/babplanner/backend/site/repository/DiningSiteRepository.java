package com.babplanner.backend.site.repository;

import com.babplanner.backend.site.domain.DiningSite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningSiteRepository extends JpaRepository<DiningSite, Long> {

    Optional<DiningSite> findByCode(String code);

    List<DiningSite> findAllByOrderBySortOrderAscIdAsc();
}

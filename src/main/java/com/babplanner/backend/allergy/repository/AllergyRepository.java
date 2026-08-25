package com.babplanner.backend.allergy.repository;

import com.babplanner.backend.allergy.domain.Allergy;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    List<Allergy> findAllByOrderBySortOrderAscIdAsc();

    List<Allergy> findByIdInOrderBySortOrderAscIdAsc(Collection<Long> ids);
}

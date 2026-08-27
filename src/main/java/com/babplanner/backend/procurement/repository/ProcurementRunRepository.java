package com.babplanner.backend.procurement.repository;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.procurement.domain.ProcurementRun;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcurementRunRepository extends JpaRepository<ProcurementRun, Long> {

    /**
     * 가장 최근 실행 하나.
     *
     * <p>여러 실행의 항목을 한 목록에 섞지 않는다. 섞이면 같은 재료가 두 줄
     * 나오고 어느 쪽이 최신인지 알 수 없다.
     */
    @EntityGraph(attributePaths = {"site", "items"})
    Optional<ProcurementRun> findFirstBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
            Long siteId, LocalDate serviceDate, MealType mealType);
}

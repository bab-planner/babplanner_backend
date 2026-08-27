package com.babplanner.backend.demand.repository;

import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.menu.domain.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandPredictionRepository extends JpaRepository<DemandPrediction, Long> {

    /**
     * 한 날짜의 실행 이력. 최신 실행이 먼저다.
     *
     * <p>같은 시각에 두 건이 저장돼도 순서가 흔들리지 않도록 id까지 함께
     * 정렬한다.
     */
    @EntityGraph(attributePaths = {"site"})
    List<DemandPrediction> findBySiteIdAndServiceDateOrderByCreatedAtDescIdDesc(
            Long siteId, LocalDate serviceDate);

    @EntityGraph(attributePaths = {"site"})
    List<DemandPrediction> findBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
            Long siteId, LocalDate serviceDate, MealType mealType);

    /** 발주 추천이 근거로 삼는 가장 최근 예측. */
    @EntityGraph(attributePaths = {"site"})
    Optional<DemandPrediction> findFirstBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
            Long siteId, LocalDate serviceDate, MealType mealType);
}

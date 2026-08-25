package com.babplanner.backend.menu.repository;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    /**
     * 날짜 필수 + 나머지 선택 조건을 AND로 건다.
     *
     * <p>선택 조건은 null이면 무시한다. Specification을 도입할 만큼 조건이
     * 많지 않아 단일 query로 둔다. 정렬은 site 노출 순서 → id 순으로 고정해
     * 앱이 재정렬하지 않아도 항상 같은 순서가 되게 한다.
     */
    @EntityGraph(attributePaths = {"site", "items"})
    @Query("""
            select m from Menu m
            join m.site s
            where m.serviceDate = :serviceDate
              and (:mealType is null or m.mealType = :mealType)
              and (:category is null or m.category = :category)
              and (:siteId is null or s.id = :siteId)
            order by s.sortOrder asc, s.id asc, m.id asc
            """)
    List<Menu> search(
            @Param("serviceDate") LocalDate serviceDate,
            @Param("mealType") MealType mealType,
            @Param("category") MenuCategory category,
            @Param("siteId") Long siteId);

    /**
     * 상세 조회.
     *
     * <p>컬렉션은 하나만 fetch join한다. items와 allergies를 같이 fetch하면
     * 곱집합이 되어 행이 중복된다. 나머지 하나는 트랜잭션 안에서 지연 로딩한다.
     */
    @EntityGraph(attributePaths = {"site", "items"})
    Optional<Menu> findWithDetailById(Long id);

    boolean existsByServiceDate(LocalDate serviceDate);
}

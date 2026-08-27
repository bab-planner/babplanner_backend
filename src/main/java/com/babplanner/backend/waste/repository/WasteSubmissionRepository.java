package com.babplanner.backend.waste.repository;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WasteSubmissionRepository extends JpaRepository<WasteSubmission, Long> {

    /** 소유자까지 함께 건다. 남의 제출을 존재조차 알 수 없게 한다. */
    Optional<WasteSubmission> findByIdAndUserId(Long id, Long userId);

    List<WasteSubmission> findByStatusIn(Collection<WasteSubmissionStatus> statuses);

    /** 이 메뉴를 가리키는 제출이 있는지. 있으면 메뉴를 지울 수 없다. */
    boolean existsByMenuId(Long menuId);

    /**
     * 기간 안에서 분석이 끝난 제출의 건수와 평균 잔반률.
     *
     * <p>기간은 제출 시각이 아니라 그 제출이 가리키는 메뉴의 제공일로 센다.
     * 어제 저녁 식사를 오늘 새벽에 올려도 어제 급식의 잔반이기 때문이다.
     *
     * <p>APPROVED이면서 wasteRate가 있는 건만 센다. 분석 실패나 거절 건을
     * 표본에 넣으면 평균이 실제로 잰 값이 아니게 된다.
     *
     * <p>집계는 DB에서 끝낸다. 행을 다 읽어 자바에서 더하면 데이터가 늘어난
     * 만큼 그대로 느려진다.
     */
    @Query("select new com.babplanner.backend.waste.repository.WasteRateAggregate("
            + "count(w), avg(w.wasteRate)) "
            + "from WasteSubmission w, Menu m "
            + "where m.id = w.menuId and w.status = :status and w.wasteRate is not null "
            + "and m.serviceDate between :from and :to")
    WasteRateAggregate aggregateWasteRate(
            @Param("status") WasteSubmissionStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 같은 기간을 메뉴별로 나눈 집계.
     *
     * <p>잔반률이 높은 메뉴부터 본다. 같으면 표본이 많은 쪽을, 그것도 같으면
     * id 순으로 둔다. 정렬이 흔들리면 같은 요청에 다른 순서가 나온다.
     */
    @Query("select new com.babplanner.backend.waste.repository.MenuWasteAggregate("
            + "w.menuId, m.name, avg(w.wasteRate), count(w)) "
            + "from WasteSubmission w, Menu m "
            + "where m.id = w.menuId and w.status = :status and w.wasteRate is not null "
            + "and m.serviceDate between :from and :to "
            + "group by w.menuId, m.name "
            + "order by avg(w.wasteRate) desc, count(w) desc, w.menuId asc")
    List<MenuWasteAggregate> aggregateWasteRateByMenu(
            @Param("status") WasteSubmissionStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 처리 시작을 원자적으로 선점한다.
     *
     * <p>재시작 복구와 정상 처리가 같은 건을 동시에 집어도 UPDATE가 성공한
     * 쪽만 실제로 처리한다. 반환값이 0이면 이미 다른 쪽이 가져갔다는 뜻이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WasteSubmission w set w.status = :next, w.processingStartedAt = :now, "
            + "w.updatedAt = :now where w.id = :id and w.status = :expected")
    int claimForProcessing(
            @Param("id") Long id,
            @Param("expected") WasteSubmissionStatus expected,
            @Param("next") WasteSubmissionStatus next,
            @Param("now") Instant now);
}

package com.babplanner.backend.waste.repository;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WasteSubmissionRepository extends JpaRepository<WasteSubmission, Long> {

    String ROW_SELECT = "select new com.babplanner.backend.waste.repository.AdminWasteSubmissionRow("
            + "w.id, u.name, u.phone, w.menuId, m.name, m.serviceDate, w.status, "
            + "w.earnedPoints, w.couponIssued, w.createdAt, w.capturedAt, w.reviewedAt) "
            + "from WasteSubmission w, UserEntity u, Menu m "
            + "where u.id = w.userId and m.id = w.menuId ";

    /** 소유자까지 함께 건다. 남의 제출을 존재조차 알 수 없게 한다. */
    Optional<WasteSubmission> findByIdAndUserId(Long id, Long userId);

    /** 이 메뉴를 가리키는 제출이 있는지. 있으면 메뉴를 지울 수 없다. */
    boolean existsByMenuId(Long menuId);

    /** 관리자 목록. 최신순, 같은 시각이면 id 역순. */
    @Query(ROW_SELECT + "order by w.createdAt desc, w.id desc")
    List<AdminWasteSubmissionRow> findRows(Pageable pageable);

    @Query(ROW_SELECT + "and w.status = :status order by w.createdAt desc, w.id desc")
    List<AdminWasteSubmissionRow> findRowsByStatus(
            @Param("status") WasteSubmissionStatus status, Pageable pageable);

    @Query(ROW_SELECT + "and w.id = :id")
    Optional<AdminWasteSubmissionRow> findRowById(@Param("id") Long id);

    /**
     * 승인 전이. 이미 APPROVED인 행은 건드리지 않는다.
     *
     * <p>두 관리자가 동시에 승인해도 UPDATE가 행을 잠그므로 한쪽만 1을 받는다. 1을
     * 받은 쪽만 포인트를 지급한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WasteSubmission w set w.status = :approved, w.earnedPoints = :points, "
            + "w.couponIssued = false, w.reviewedAt = :now, w.reviewedByUserId = :reviewer, "
            + "w.updatedAt = :now where w.id = :id and w.status <> :approved")
    int approveIfNotApproved(
            @Param("id") Long id,
            @Param("approved") WasteSubmissionStatus approved,
            @Param("points") int points,
            @Param("reviewer") Long reviewer,
            @Param("now") Instant now);

    /** 반려 전이. APPROVED와 이미 REJECTED인 행은 건드리지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WasteSubmission w set w.status = :rejected, w.earnedPoints = 0, "
            + "w.couponIssued = false, w.reviewedAt = :now, w.reviewedByUserId = :reviewer, "
            + "w.updatedAt = :now where w.id = :id and w.status not in :terminal")
    int rejectIfOpen(
            @Param("id") Long id,
            @Param("rejected") WasteSubmissionStatus rejected,
            @Param("terminal") Collection<WasteSubmissionStatus> terminal,
            @Param("reviewer") Long reviewer,
            @Param("now") Instant now);

    /**
     * 기간 안에서 검토가 끝난 제출의 건수와 평균 잔반률.
     *
     * <p>기간은 제출 시각이 아니라 그 제출이 가리키는 메뉴의 제공일로 센다.
     * 건수는 검토된(reviewed) 제출 전체이고, 평균은 APPROVED이면서 잔반률이 기록된
     * 건만으로 낸다. 잔반률이 없으면 평균은 null이다.
     */
    @Query("select new com.babplanner.backend.waste.repository.WasteRateAggregate("
            + "count(w), avg(case when w.status = :measured then w.wasteRate else null end)) "
            + "from WasteSubmission w, Menu m "
            + "where m.id = w.menuId and w.status in :reviewed "
            + "and m.serviceDate between :from and :to")
    WasteRateAggregate aggregateWasteRate(
            @Param("measured") WasteSubmissionStatus measured,
            @Param("reviewed") Collection<WasteSubmissionStatus> reviewed,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 같은 기간을 메뉴별로 나눈 집계. 잔반률이 기록된 APPROVED 건만 센다.
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
}

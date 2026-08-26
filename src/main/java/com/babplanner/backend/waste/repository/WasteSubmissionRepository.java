package com.babplanner.backend.waste.repository;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
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

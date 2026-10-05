package com.babplanner.backend.reward.repository;

import com.babplanner.backend.reward.domain.RewardAccount;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardAccountRepository extends JpaRepository<RewardAccount, Long> {

    Optional<RewardAccount> findByUserId(Long userId);

    /** 잔액을 원자적으로 더한다. 읽고 쓰는 사이의 갱신 손실이 없다. */
    @Modifying(flushAutomatically = true)
    @Query("update RewardAccount a set a.points = a.points + :amount, a.updatedAt = :now "
            + "where a.userId = :userId")
    int addPoints(
            @Param("userId") Long userId,
            @Param("amount") int amount,
            @Param("now") Instant now);
}

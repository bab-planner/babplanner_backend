package com.babplanner.backend.auth.repository;

import com.babplanner.backend.auth.domain.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    /**
     * 아직 살아 있는 세션만 원자적으로 revoke한다.
     *
     * <p>영향받은 행 수가 1이어야 회전을 이어간다. 동시에 같은 refresh token으로
     * 두 요청이 들어오면 한쪽만 1을 받고 다른 쪽은 0을 받아 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshSession s set s.revokedAt = :now, s.lastRotatedAt = :now "
            + "where s.id = :id and s.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}

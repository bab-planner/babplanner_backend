package com.babplanner.backend.reward.repository;

import com.babplanner.backend.reward.domain.Coupon;
import com.babplanner.backend.reward.domain.CouponStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findByUserId(Long userId);

    Optional<Coupon> findByIdAndUserId(Long id, Long userId);

    /**
     * 사용 가능한 쿠폰만 원자적으로 사용 처리한다.
     *
     * <p>영향받은 행이 1일 때만 사용에 성공한 것이다. 같은 쿠폰으로 동시에 두
     * 요청이 들어와도 한쪽만 1을 받는다. 만료 조건까지 WHERE에 넣어 만료된
     * 쿠폰이 사용되지 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.status = :used, c.usedAt = :now "
            + "where c.id = :id and c.userId = :userId and c.status = :available "
            + "and (c.expiresAt is null or c.expiresAt > :now)")
    int redeemIfAvailable(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("available") CouponStatus available,
            @Param("used") CouponStatus used);
}

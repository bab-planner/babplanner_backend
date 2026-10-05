package com.babplanner.backend.auth.repository;

import com.babplanner.backend.auth.domain.PhoneVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /** 같은 번호로 여러 번 요청할 수 있으므로 가장 최근 challenge를 본다. */
    Optional<PhoneVerification> findFirstByPhoneOrderByIdDesc(String phone);

    /** 시도 횟수가 한도 미만일 때만 1 올린다. 1이면 이번 시도가 허용된 것이다. */
    @Modifying(flushAutomatically = true)
    @Query("update PhoneVerification v set v.failedAttempts = v.failedAttempts + 1 "
            + "where v.id = :id and v.failedAttempts < :max")
    int reserveAttempt(@Param("id") Long id, @Param("max") int max);

    /** 성공한 시도의 슬롯을 돌려준다. */
    @Modifying(flushAutomatically = true)
    @Query("update PhoneVerification v set v.failedAttempts = v.failedAttempts - 1 "
            + "where v.id = :id and v.failedAttempts > 0")
    int releaseAttempt(@Param("id") Long id);

    Optional<PhoneVerification> findByVerificationTokenHash(String verificationTokenHash);
}

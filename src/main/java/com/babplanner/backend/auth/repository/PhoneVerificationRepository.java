package com.babplanner.backend.auth.repository;

import com.babplanner.backend.auth.domain.PhoneVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /** 같은 번호로 여러 번 요청할 수 있으므로 가장 최근 challenge를 본다. */
    Optional<PhoneVerification> findFirstByPhoneOrderByIdDesc(String phone);

    Optional<PhoneVerification> findByVerificationTokenHash(String verificationTokenHash);
}

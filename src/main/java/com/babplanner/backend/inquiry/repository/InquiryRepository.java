package com.babplanner.backend.inquiry.repository;

import com.babplanner.backend.inquiry.domain.Inquiry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 본인 것만 조회한다. 남의 문의는 존재 여부도 알 수 없어야 한다. */
    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);
}

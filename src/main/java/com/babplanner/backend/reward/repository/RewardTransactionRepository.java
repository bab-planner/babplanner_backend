package com.babplanner.backend.reward.repository;

import com.babplanner.backend.reward.domain.RewardTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    Optional<RewardTransaction> findByReferenceTypeAndReferenceId(
            String referenceType, Long referenceId);

    /** 최신순. 같은 시각이면 id 역순으로 잘라 순서가 흔들리지 않게 한다. */
    List<RewardTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}

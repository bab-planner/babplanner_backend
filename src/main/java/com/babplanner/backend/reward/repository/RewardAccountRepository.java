package com.babplanner.backend.reward.repository;

import com.babplanner.backend.reward.domain.RewardAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardAccountRepository extends JpaRepository<RewardAccount, Long> {

    Optional<RewardAccount> findByUserId(Long userId);
}

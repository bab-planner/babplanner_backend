package com.babplanner.backend.user.repository;

import com.babplanner.backend.user.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByStudentId(String studentId);
}

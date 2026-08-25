package com.babplanner.backend.allergy.repository;

import com.babplanner.backend.allergy.domain.UserAllergy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAllergyRepository extends JpaRepository<UserAllergy, Long> {

    List<UserAllergy> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

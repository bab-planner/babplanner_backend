package com.babplanner.backend.survey.repository;

import com.babplanner.backend.survey.domain.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    boolean existsByUserIdAndSurveyId(Long userId, Long surveyId);
}

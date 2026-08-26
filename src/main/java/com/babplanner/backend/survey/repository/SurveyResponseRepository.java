package com.babplanner.backend.survey.repository;

import com.babplanner.backend.survey.domain.SurveyResponse;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    boolean existsByUserIdAndSurveyId(Long userId, Long surveyId);

    /** 결과 집계용. 응답과 답변을 함께 읽는다. */
    @EntityGraph(attributePaths = {"answers"})
    List<SurveyResponse> findAllBySurveyId(Long surveyId);

    /** 응답한 사람 수. 답변 개수가 아니다. */
    long countBySurveyId(Long surveyId);
}

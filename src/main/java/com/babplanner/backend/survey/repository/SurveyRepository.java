package com.babplanner.backend.survey.repository;

import com.babplanner.backend.survey.domain.Survey;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    /**
     * 지금 참여할 수 있는 설문.
     *
     * <p>여러 개가 겹치면 가장 최근에 시작한 것을 본다. 기간 밖 설문은
     * 돌려주지 않는다.
     */
    @EntityGraph(attributePaths = {"questions"})
    @Query("select s from Survey s where s.startsAt <= :now and s.endsAt > :now "
            + "order by s.startsAt desc, s.id desc")
    Optional<Survey> findActive(@Param("now") Instant now, Limit limit);

    @EntityGraph(attributePaths = {"questions"})
    Optional<Survey> findWithQuestionsById(Long id);
}

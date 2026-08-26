package com.babplanner.backend.survey.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 사용자의 한 설문 제출.
 *
 * <p>(user_id, survey_id)에 unique 제약이 있어 중복 참여는 DB가 막는다.
 */
@Entity
@Table(name = "survey_responses")
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    private List<SurveyAnswer> answers = new ArrayList<>();

    protected SurveyResponse() {
        // JPA 전용
    }

    public SurveyResponse(Long surveyId, Long userId, Instant submittedAt) {
        this.surveyId = surveyId;
        this.userId = userId;
        this.submittedAt = submittedAt;
    }

    public void addAnswer(SurveyAnswer answer) {
        answers.add(answer);
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<SurveyAnswer> getAnswers() {
        return answers;
    }
}

package com.babplanner.backend.survey.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI v3.1 SurveyQuestion.
 *
 * <p>계약의 required 필드는 Java에서도 예약어가 아니지만 뜻을 분명히 하려고
 * isRequired로 두고 JSON 경계에서만 required 키로 바꾼다.
 */
@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SurveyQuestionType type;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 선택형 보기. 순서를 보존한다. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "survey_question_options",
            joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "label", nullable = false, length = 100)
    private List<String> options = new ArrayList<>();

    protected SurveyQuestion() {
        // JPA 전용
    }

    public SurveyQuestion(
            String text,
            SurveyQuestionType type,
            boolean isRequired,
            int sortOrder,
            List<String> options) {
        this.text = text;
        this.type = type;
        this.isRequired = isRequired;
        this.sortOrder = sortOrder;
        this.options = new ArrayList<>(options);
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public SurveyQuestionType getType() {
        return type;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<String> getOptions() {
        return options;
    }
}

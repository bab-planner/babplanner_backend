package com.babplanner.backend.survey.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
 * 질문 하나에 대한 답.
 *
 * <p>질문 종류에 따라 쓰이는 필드가 다르다. 어떤 조합이 유효한지는
 * SurveyService가 검증하고, 여기서는 저장만 한다.
 */
@Entity
@Table(name = "survey_answers")
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "scale_value")
    private Integer scaleValue;

    @Column(name = "text_value", length = 1000)
    private String textValue;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "survey_answer_options", joinColumns = @JoinColumn(name = "answer_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "label", nullable = false, length = 100)
    private List<String> selectedOptions = new ArrayList<>();

    protected SurveyAnswer() {
        // JPA 전용
    }

    public SurveyAnswer(
            Long questionId, List<String> selectedOptions, Integer scaleValue, String textValue) {
        this.questionId = questionId;
        this.selectedOptions = new ArrayList<>(selectedOptions);
        this.scaleValue = scaleValue;
        this.textValue = textValue;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Integer getScaleValue() {
        return scaleValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public List<String> getSelectedOptions() {
        return selectedOptions;
    }
}

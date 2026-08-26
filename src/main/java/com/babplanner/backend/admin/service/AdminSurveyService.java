package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.SurveyQuestionWriteRequest;
import com.babplanner.backend.admin.dto.SurveyResultResponse;
import com.babplanner.backend.admin.dto.SurveyWriteRequest;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyAnswer;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.domain.SurveyResponse;
import com.babplanner.backend.survey.dto.SurveyResponseView;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.babplanner.backend.survey.repository.SurveyResponseRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 설문 관리.
 *
 * <p>사용자가 답하는 것과 같은 Survey를 쓴다. 문항 유형과 답변 규칙은
 * 사용자 쪽 검증과 같은 것을 따른다.
 */
@Service
public class AdminSurveyService {

    /** SCALE 문항에 라벨이 없을 때의 범위. 사용자 쪽 답변 검증과 같은 값이다. */
    private static final int DEFAULT_SCALE_RANGE = 5;

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final Clock clock;

    public AdminSurveyService(
            SurveyRepository surveyRepository,
            SurveyResponseRepository responseRepository,
            Clock clock) {
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.clock = clock;
    }

    /** 지난 설문과 예정된 설문까지 모두. 진행 중인 것만 주면 관리가 안 된다. */
    @Transactional(readOnly = true)
    public List<SurveyResponseView> getSurveys() {
        return surveyRepository.findAllByOrderByStartsAtDescIdDesc().stream()
                .map(SurveyResponseView::from)
                .toList();
    }

    @Transactional
    public SurveyResponseView create(SurveyWriteRequest request) {
        if (!request.startsAt().isBefore(request.endsAt())) {
            // 시작과 끝이 같거나 뒤집힌 설문은 열려 있는 순간이 없다.
            throw new ApiException(ErrorCode.INVALID_SURVEY_PERIOD);
        }

        Survey survey = new Survey(
                request.title(),
                request.description(),
                request.startsAt(),
                request.endsAt(),
                clock.instant());

        int sortOrder = 1;
        for (SurveyQuestionWriteRequest question : request.questions()) {
            validate(question);
            survey.addQuestion(new SurveyQuestion(
                    question.text(),
                    question.type(),
                    question.requiredOrDefault(),
                    sortOrder++,
                    question.optionsOrEmpty()));
        }

        return SurveyResponseView.from(surveyRepository.save(survey));
    }

    /**
     * 문항 하나가 답할 수 있는 모양인지 본다.
     *
     * <p>선택지 없는 객관식은 아무도 답할 수 없고, 선택지 붙은 주관식은
     * 그 선택지가 어디에도 쓰이지 않는다. 둘 다 만든 사람의 착각이다.
     */
    private void validate(SurveyQuestionWriteRequest question) {
        List<String> options = question.optionsOrEmpty();
        boolean hasBlank = options.stream().anyMatch(option -> option == null || option.isBlank());

        switch (question.type()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                if (options.size() < 2 || hasBlank) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_QUESTION);
                }
            }
            case SCALE -> {
                // 라벨은 선택이다. 없으면 1~5로 본다. 주면 그 개수가 범위가 된다.
                if (!options.isEmpty() && (options.size() < 2 || hasBlank)) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_QUESTION);
                }
            }
            case TEXT -> {
                if (!options.isEmpty()) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_QUESTION);
                }
            }
        }
    }

    /**
     * 설문 결과.
     *
     * <p>누가 답했는지는 담지 않는다. 관리자가 알아야 하는 것은 무엇이 얼마나
     * 나왔는가이지 누가 그렇게 답했는가가 아니다.
     */
    @Transactional(readOnly = true)
    public SurveyResultResponse getResults(Long surveyId) {
        Survey survey = surveyRepository
                .findWithQuestionsById(surveyId)
                .orElseThrow(() -> new ApiException(ErrorCode.SURVEY_NOT_FOUND));

        List<SurveyResponse> responses = responseRepository.findAllBySurveyId(surveyId);
        Map<Long, List<SurveyAnswer>> byQuestion = new LinkedHashMap<>();
        for (SurveyResponse response : responses) {
            for (SurveyAnswer answer : response.getAnswers()) {
                byQuestion.computeIfAbsent(answer.getQuestionId(), key -> new ArrayList<>())
                        .add(answer);
            }
        }

        List<SurveyResultResponse.QuestionResult> results = survey.getQuestions().stream()
                .map(question -> new SurveyResultResponse.QuestionResult(
                        question.getId(),
                        summarize(question, byQuestion.getOrDefault(question.getId(), List.of()))))
                .toList();

        // 응답한 사람 수다. 한 사람이 네 문항에 답했다고 네 명으로 세지 않는다.
        return new SurveyResultResponse(surveyId, responses.size(), results);
    }

    private Map<String, Object> summarize(SurveyQuestion question, List<SurveyAnswer> answers) {
        return switch (question.getType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> choiceSummary(question, answers);
            case SCALE -> scaleSummary(question, answers);
            case TEXT -> textSummary(answers);
        };
    }

    /** 선택지별 개수. 아무도 고르지 않은 선택지도 0으로 남긴다. */
    private Map<String, Object> choiceSummary(
            SurveyQuestion question, List<SurveyAnswer> answers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String option : question.getOptions()) {
            counts.put(option, 0);
        }
        for (SurveyAnswer answer : answers) {
            for (String selected : answer.getSelectedOptions()) {
                counts.merge(selected, 1, Integer::sum);
            }
        }
        return Map.of("responseCount", answers.size(), "optionCounts", counts);
    }

    private Map<String, Object> scaleSummary(SurveyQuestion question, List<SurveyAnswer> answers) {
        int range = question.getOptions().isEmpty()
                ? DEFAULT_SCALE_RANGE
                : question.getOptions().size();

        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int value = 1; value <= range; value++) {
            distribution.put(String.valueOf(value), 0);
        }

        long total = 0;
        int counted = 0;
        for (SurveyAnswer answer : answers) {
            Integer value = answer.getScaleValue();
            if (value == null) {
                continue;
            }
            distribution.merge(String.valueOf(value), 1, Integer::sum);
            total += value;
            counted++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("responseCount", counted);
        // 답이 없으면 평균도 없다. 0으로 두면 "전부 최하점"으로 읽힌다.
        summary.put("average", counted == 0 ? null : (double) total / counted);
        summary.put("distribution", distribution);
        return summary;
    }

    /**
     * 주관식 답변.
     *
     * <p>답변 내용은 관리자가 봐야 하는 것이지만 누가 썼는지는 붙이지 않는다.
     */
    private Map<String, Object> textSummary(List<SurveyAnswer> answers) {
        List<String> texts = answers.stream()
                .map(SurveyAnswer::getTextValue)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        return Map.of("responseCount", texts.size(), "responses", texts);
    }
}

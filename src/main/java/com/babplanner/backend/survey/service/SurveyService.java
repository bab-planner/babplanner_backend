package com.babplanner.backend.survey.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyAnswer;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyResponse;
import com.babplanner.backend.survey.dto.SurveyAnswerRequest;
import com.babplanner.backend.survey.dto.SurveyResponseView;
import com.babplanner.backend.survey.dto.SurveySubmitRequest;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.babplanner.backend.survey.repository.SurveyResponseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활성 설문 조회와 응답 제출.
 *
 * <p>참여 여부를 미리 알려주는 API는 계약에 없다. 중복 참여는 제출 시 409로만
 * 판정한다.
 *
 * <p>설문 제출이 포인트를 지급하지 않는다. 지급 규칙이 아직 확정되지 않았다.
 */
@Service
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final Clock clock;

    public SurveyService(
            SurveyRepository surveyRepository,
            SurveyResponseRepository responseRepository,
            Clock clock) {
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.clock = clock;
    }

    /** 참여 가능한 설문이 없으면 null이다(계약상 200 + data null). */
    @Transactional(readOnly = true)
    public SurveyResponseView getActiveSurvey() {
        return surveyRepository
                .findActive(clock.instant(), Limit.of(1))
                .map(SurveyResponseView::from)
                .orElse(null);
    }

    @Transactional
    public void submit(Long userId, Long surveyId, SurveySubmitRequest request) {
        Instant now = clock.instant();
        Survey survey = surveyRepository
                .findWithQuestionsById(surveyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (responseRepository.existsByUserIdAndSurveyId(userId, surveyId)) {
            throw new ApiException(ErrorCode.SURVEY_ALREADY_SUBMITTED);
        }

        Map<Long, SurveyQuestion> questions = new LinkedHashMap<>();
        for (SurveyQuestion question : survey.getQuestions()) {
            questions.put(question.getId(), question);
        }

        SurveyResponse response = new SurveyResponse(surveyId, userId, now);
        Set<Long> answered = new HashSet<>();

        for (SurveyAnswerRequest answer : request.answers()) {
            SurveyQuestion question = questions.get(answer.questionId());
            // 다른 설문의 질문 id는 받지 않는다.
            if (question == null) {
                throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
            }
            if (!answered.add(answer.questionId())) {
                throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
            }
            validateShape(question, answer);
            response.addAnswer(new SurveyAnswer(
                    answer.questionId(),
                    answer.selectedOptionsOrEmpty(),
                    answer.scaleValue(),
                    answer.textValue()));
        }

        for (SurveyQuestion question : survey.getQuestions()) {
            if (question.isRequired() && !answered.contains(question.getId())) {
                throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
            }
        }

        try {
            responseRepository.saveAndFlush(response);
        } catch (DataIntegrityViolationException exception) {
            // 동시에 두 요청이 들어오면 unique 제약이 한쪽을 막는다.
            throw new ApiException(ErrorCode.SURVEY_ALREADY_SUBMITTED);
        }
    }

    /** 질문 종류에 맞는 필드만 채워졌는지 확인한다. */
    private void validateShape(SurveyQuestion question, SurveyAnswerRequest answer) {
        List<String> selected = answer.selectedOptionsOrEmpty();
        boolean hasScale = answer.scaleValue() != null;
        boolean hasText = answer.textValue() != null && !answer.textValue().isBlank();

        switch (question.getType()) {
            case SINGLE_CHOICE -> {
                if (selected.size() != 1 || hasScale || hasText) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
                requireKnownOptions(question, selected);
            }
            case MULTIPLE_CHOICE -> {
                if (selected.isEmpty() || hasScale || hasText) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
                requireKnownOptions(question, selected);
            }
            case SCALE -> {
                if (!hasScale || !selected.isEmpty() || hasText) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
                int range = question.getOptions().isEmpty() ? 5 : question.getOptions().size();
                if (answer.scaleValue() < 1 || answer.scaleValue() > range) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
            }
            case TEXT -> {
                if (!selected.isEmpty() || hasScale) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
                if (question.isRequired() && !hasText) {
                    throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
                }
            }
        }
    }

    /** 보기에 없는 값을 임의로 보낼 수 없다. */
    private void requireKnownOptions(SurveyQuestion question, List<String> selected) {
        Set<String> known = new HashSet<>(question.getOptions());
        for (String option : selected) {
            if (!known.contains(option)) {
                throw new ApiException(ErrorCode.INVALID_SURVEY_ANSWER);
            }
        }
    }
}

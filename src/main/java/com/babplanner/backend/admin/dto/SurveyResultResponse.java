package com.babplanner.backend.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI SurveyResultEnvelope.data.
 *
 * <p>summary는 계약이 열어 둔(additionalProperties) 자리다. 문항 유형마다
 * 모양이 다르므로 Map으로 둔다.
 *
 * @param responseCount 응답한 사람 수. 답변 개수가 아니다. 한 사람이 네 문항에
 *     답했다고 네 명으로 세지 않는다.
 */
public record SurveyResultResponse(
        Long surveyId, long responseCount, List<QuestionResult> questionResults) {

    /** 문항 하나의 집계. 누가 답했는지는 담지 않는다. */
    public record QuestionResult(Long questionId, Map<String, Object> summary) {}
}

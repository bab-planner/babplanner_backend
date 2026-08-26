package com.babplanner.backend.admin.dto;

import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * OpenAPI InquiryAdminUpdateRequest. 부분 수정이다.
 *
 * <p>adminAnswer를 JsonNode로 받는다. 그래야 "필드를 안 보냈다"와 "null을
 * 보내 지워 달라"를 구분할 수 있다. 둘을 구분하지 못하면 상태만 바꾸려는
 * 요청이 답변을 지워 버린다.
 *
 * <p>Optional로도 될 것 같지만 되지 않는다. Jackson은 없는 필드를 빈
 * Optional로 채워서 두 경우가 같아진다.
 *
 * @param status 보내지 않으면 그대로 둔다
 * @param adminAnswer 자바 null이면 손대지 않고, JSON null이면 지운다
 */
public record InquiryAdminUpdateRequest(InquiryStatus status, JsonNode adminAnswer) {

    /** 아무것도 바꾸지 않는 요청인지. */
    public boolean isEmpty() {
        return status == null && adminAnswer == null;
    }

    /** 답변을 건드리는 요청인지. */
    public boolean touchesAnswer() {
        return adminAnswer != null;
    }

    /** 새 답변. 지우라는 요청이면 null. */
    public String answerOrNull() {
        return adminAnswer == null || adminAnswer.isNull() ? null : adminAnswer.asText();
    }

    /** 답변 자리에 문자열도 null도 아닌 값이 온 경우. */
    public boolean hasInvalidAnswer() {
        return adminAnswer != null && !adminAnswer.isNull() && !adminAnswer.isTextual();
    }
}

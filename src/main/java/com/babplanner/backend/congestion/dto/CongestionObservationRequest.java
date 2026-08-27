package com.babplanner.backend.congestion.dto;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

/**
 * OpenAPI CongestionObservationRequest.
 *
 * <p>수집기가 보내는 것은 <b>이미 판정이 끝난 비식별 지표</b>다. CCTV 원본
 * 프레임이나 사진은 이 경로로 오지 않는다. 영상이 서비스 백엔드에 쌓이기
 * 시작하면 그 순간부터 다른 종류의 시스템이 된다.
 *
 * <p>level을 함께 받는다. 인원수만 받고 서버가 단계를 계산하려면 임계값을
 * 정해야 하는데, 그 임계값은 식당 규모와 회전율에 따라 다르고 정해진 적이
 * 없다. 지어낸 임계값으로 단계를 만들면 화면에 뜬 "여유"가 무엇을 뜻하는지
 * 아무도 설명할 수 없다.
 *
 * <p>source는 받지 않는다. 어디서 들어온 값인지는 서버가 기록한다. 보내는
 * 쪽이 정하게 하면 저장된 출처를 신뢰할 수 없다.
 *
 * @param siteId 어느 식당인지
 * @param deviceId 관측 장비 식별자. 사람이 아니라 장비를 가리킨다
 * @param peopleCount 관측 인원. 음수는 관측일 수 없다
 * @param level 판정 단계. 수집기가 정한다
 * @param observedAt 관측 시각
 * @param displayMessage 표시 문구. 없어도 된다
 * @param modelVersion 판정 모델 버전. 있으면 공백이 아니어야 한다
 */
public record CongestionObservationRequest(
        @NotNull Long siteId,
        @NotBlank String deviceId,
        @NotNull @PositiveOrZero Integer peopleCount,
        @NotNull CongestionLevel level,
        @NotNull OffsetDateTime observedAt,
        String displayMessage,
        String modelVersion) {

    /** 값을 보냈으면서 공백인 경우. 빈 문자열을 버전으로 저장하지 않는다. */
    public boolean hasBlankModelVersion() {
        return modelVersion != null && modelVersion.isBlank();
    }

    public String normalizedModelVersion() {
        return modelVersion == null ? null : modelVersion.strip();
    }

    public String normalizedDisplayMessage() {
        if (displayMessage == null || displayMessage.isBlank()) {
            return null;
        }
        return displayMessage.strip();
    }
}

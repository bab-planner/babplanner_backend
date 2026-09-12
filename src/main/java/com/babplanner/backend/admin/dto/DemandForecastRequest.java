package com.babplanner.backend.admin.dto;

import com.babplanner.backend.menu.domain.MenuCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 관리자 식수 예측 요청 (합성데이터 PoC).
 *
 * <p>지금은 예측에 필요한 조건을 관리자가 전부 입력한다. 최종 제품에서는 대부분을
 * 백엔드가 스스로 채운다. 그 전까지의 임시 입력이다.
 *
 * <ul>
 *   <li>TODO(demand-forecast): {@code menuPopularity}는 지금 클라이언트가 보내는
 *       <b>PoC 시뮬레이션 값</b>이다. 향후 과거 실제 메뉴별 식수 기록으로 Spring 또는
 *       AI feature pipeline이 자동 계산한다. 일반 관리자에게 0.9 같은 ML feature 값을
 *       직접 입력받지 않을 계획이다.
 *   <li>TODO(demand-forecast): {@code temperature}/{@code rainfall}/{@code humidity}는
 *       지금 입력값이다. 향후 Spring이 기상예보 API로 D-1 예보를 받아 자동 구성한다.
 *   <li>TODO(demand-forecast): {@code semester}/{@code semesterWeek}/{@code examPeriod}/
 *       {@code holiday}/{@code campusEvent}는 지금 입력값이다. 향후 Spring DB의
 *       학사일정으로 자동 계산한다.
 * </ul>
 *
 * <p>검증은 명백히 틀린 값만 여기서 막는다. 최종 판단은 AI 서버 계약이 한다.
 * 범위는 AI 서버와 같게 둬서, 여기를 통과한 값이 AI 서버에서 거절되는 일을 줄인다.
 *
 * <p>예측 인원이나 과거 식수(lag)를 받지 않는다. 받을 수 있게 하면 예측 서비스를
 * 우회해 원하는 숫자를 만들 수 있다.
 *
 * @param menuCategory 다른 메뉴 API와 같은 enum 이름(KOREAN, WESTERN 등). AI 서버로는
 *     한글 라벨로 바꿔 보낸다
 */
public record DemandForecastRequest(
        @NotNull LocalDate date,
        @NotNull @Min(1) @Max(2) Integer semester,
        @NotNull @Min(1) @Max(16) Integer semesterWeek,
        @NotNull Boolean examPeriod,
        @NotNull Boolean holiday,
        @NotNull Boolean campusEvent,
        @NotNull @DecimalMin("-30.0") @DecimalMax("45.0") Double temperature,
        @NotNull @DecimalMin("0.0") @DecimalMax("500.0") Double rainfall,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double humidity,
        @NotNull MenuCategory menuCategory,
        @NotBlank @Size(max = 50) String mainMenu,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double menuPopularity,
        @NotNull Boolean specialMenu) {

    /** 모델은 학기 중 평일만 보고 학습했다. 주말에 숫자를 내면 근거 없는 값이다. */
    @AssertTrue(message = "주말은 예측 대상이 아닙니다.")
    public boolean isDateOnWeekday() {
        return date == null
                || (date.getDayOfWeek() != DayOfWeek.SATURDAY
                        && date.getDayOfWeek() != DayOfWeek.SUNDAY);
    }
}

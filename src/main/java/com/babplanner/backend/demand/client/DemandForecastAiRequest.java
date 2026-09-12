package com.babplanner.backend.demand.client;

import com.babplanner.backend.menu.domain.MenuCategory;

/**
 * FastAPI {@code DemandForecastRequest}. 필드 이름과 값 표현이 FastAPI 계약과 1:1이다.
 *
 * <p>관리자 API의 요청({@code admin.dto.DemandForecastRequest})과 일부러 나눠 둔다.
 * 관리자 API는 이 백엔드의 계약이고, 이것은 AI 서버의 내부 계약이다. 한쪽이 바뀔
 * 때 다른 쪽이 조용히 따라 바뀌면 안 된다.
 *
 * <p>과거 식수와 과거 CCTV 값은 보내지 않는다. AI 서버가 자기 이력에서 예측일
 * 이전 기록만으로 만든다. 사용자 정보도 보내지 않는다. 예측에 필요하지 않다.
 *
 * @param date 예측일. ISO-8601 문자열로 보낸다. ObjectMapper 설정에 따라 날짜가
 *     배열로 직렬화되는 일이 없게 한다
 * @param menuCategory FastAPI가 받는 한글 라벨(한식·중식·양식·일식·분식·기타)
 */
public record DemandForecastAiRequest(
        String date,
        int semester,
        int semesterWeek,
        boolean examPeriod,
        boolean holiday,
        boolean campusEvent,
        double temperature,
        double rainfall,
        double humidity,
        String menuCategory,
        String mainMenu,
        double menuPopularity,
        boolean specialMenu) {

    /**
     * 백엔드 enum을 FastAPI가 받는 한글 라벨로 바꾼다.
     *
     * <p>관리자 API는 다른 메뉴 API와 같이 enum 이름(KOREAN 등)을 쓴다. 한글 값은
     * AI 서버 계약에만 있다. switch에 default를 두지 않아 enum이 늘어나면
     * 컴파일이 실패한다. 모르는 값을 아무 라벨로나 보내지 않는다.
     */
    public static String menuCategoryLabel(MenuCategory category) {
        return switch (category) {
            case KOREAN -> "한식";
            case CHINESE -> "중식";
            case WESTERN -> "양식";
            case JAPANESE -> "일식";
            case SNACK -> "분식";
            case OTHER -> "기타";
        };
    }
}

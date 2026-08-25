package com.babplanner.backend.menu.domain;

/**
 * OpenAPI v3.1 MenuCategory 열거값.
 *
 * <p>앱의 필터 칩이 5종만 보여주더라도 계약에는 OTHER가 있으므로 줄이지 않는다.
 */
public enum MenuCategory {
    KOREAN,
    SNACK,
    CHINESE,
    JAPANESE,
    WESTERN,
    OTHER
}
